package org.Azkerash.create_bioships.components.logic.stomach;

import com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity;
import com.github.elenterius.biomancy.crafting.recipe.DecomposingRecipe;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.inventory.*;
import org.Azkerash.create_bioships.core.*;

/** Autonomous acid/digestion. Intestines deliver input; players inspect but cannot insert. */
public final class StomachBlockEntity extends OrganInventory implements IHaveGoggleInformation {
  @Override
  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = new CompoundTag();
    tag.putInt("Acid", acid);
    tag.putInt("Progress", progress);
    tag.putInt("Duration", duration);
    tag.putInt("Queued", queued());
    return tag;
  }

  @Override
  public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
    acid = tag.getInt("Acid");
    progress = tag.getInt("Progress");
    duration = tag.getInt("Duration");
    clientQueued = tag.getInt("Queued");
  }

  private int clientQueued;
  public static final int INPUT_SLOTS = 9, MAX_ACID = 1000, EAT_TICKS = 32, HUNGER_PER_PASTE = 4;
  private int acid = 200, progress, duration, activeSlot = -1, eating;
  private String recipeId = "";
  private NonNullList<ItemStack> pending = NonNullList.create();

  public StomachBlockEntity(BlockPos pos, BlockState state) {
    super(ComponentRegistry.STOMACH_DISPLAY.get(), pos, state, 27);
  }

  public static Optional<RecipeHolder<DecomposingRecipe>> recipe(Level level, ItemStack stack) {
    return stack.isEmpty()
        ? Optional.empty()
        : level
            .getRecipeManager()
            .getRecipeFor(
                DecomposerBlockEntity.RECIPE_TYPE.get(), new SingleRecipeInput(stack), level);
  }

  public static boolean accepts(Level level, ItemStack stack) {
    return recipe(level, stack).isPresent();
  }

  public ItemStack enqueue(ItemStack stack, boolean simulate) {
    return level != null && accepts(level, stack)
        ? insert(stack, 0, INPUT_SLOTS, simulate)
        : stack.copy();
  }

  public int acid() {
    return acid;
  }

  public int progress() {
    return progress;
  }

  public int duration() {
    return duration;
  }

  public int queued() {
    if (level != null && level.isClientSide) return clientQueued;
    return items.subList(0, INPUT_SLOTS).stream().mapToInt(ItemStack::getCount).sum();
  }

  public static Item paste() {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:nutrient_paste"));
  }

  public static void tick(Level level, BlockPos pos, BlockState state, StomachBlockEntity stomach) {
    stomach.advance();
  }

  public void advance() {
    if (!(level instanceof ServerLevel server)) return;
    if (level.getGameTime() % 20 == 0)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    var body = CoreAssemblyRules.containing(server, worldPosition);
    if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) return;
    if (acid < MAX_ACID) {
      acid++;
      setChanged();
    }
    if (SubLevelData.hunger(body) < SubLevelData.maximumHunger(body)) {
      int pasteSlot = -1;
      for (int i = INPUT_SLOTS; i < items.size(); i++)
        if (items.get(i).is(paste())) {
          pasteSlot = i;
          break;
        }
      if (pasteSlot >= 0) {
        if (++eating >= EAT_TICKS) {
          items.get(pasteSlot).shrink(1);
          SubLevelData.hunger(body, SubLevelData.hunger(body) + HUNGER_PER_PASTE);
          eating = 0;
        }
        setChanged();
      } else eating = 0;
    } else eating = 0;
    if (activeSlot < 0) {
      for (int slot = 0; slot < INPUT_SLOTS; slot++) {
        var match = recipe(level, items.get(slot));
        if (match.isEmpty()) continue;
        var value = match.get().value();
        if (items.get(slot).getCount() < value.getIngredientQuantity().count()) continue;
        int cost = Math.clamp(value.getCraftingCostNutrients() * 100, 50, MAX_ACID);
        if (acid < cost) continue;
        acid -= cost;
        activeSlot = slot;
        recipeId = match.get().id().toString();
        duration = Math.max(1, value.getCraftingTimeTicks());
        progress = 0;
        pending = NonNullList.create();
        for (var output : value.getOutputs()) {
          var result = output.getItemStack(level.random);
          if (!result.isEmpty()) pending.add(result.copy());
        }
        if (items.get(slot).has(DataComponents.FOOD) && !items.get(slot).is(paste()))
          pending.add(new ItemStack(paste(), 1));
        setChanged();
        break;
      }
    }
    if (activeSlot < 0) return;
    var match = recipe(level, items.get(activeSlot));
    if (match.isEmpty()
        || !match.get().id().toString().equals(recipeId)
        || items.get(activeSlot).getCount() < match.get().value().getIngredientQuantity().count()) {
      // Inputs are read-only during processing. Commands invalidating a recipe cancel it without
      // outputs.
      activeSlot = -1;
      progress = duration = 0;
      pending = NonNullList.create();
      setChanged();
      return;
    }
    if (progress < duration) {
      progress++;
      setChanged();
    }
    if (progress < duration) return;
    var snapshot = NonNullList.withSize(items.size(), ItemStack.EMPTY);
    for (int i = 0; i < items.size(); i++) snapshot.set(i, items.get(i).copy());
    boolean fits = true;
    for (var output : pending)
      if (!insert(output, INPUT_SLOTS, items.size(), false).isEmpty()) {
        fits = false;
        break;
      }
    if (!fits) {
      for (int i = 0; i < items.size(); i++) items.set(i, snapshot.get(i));
      return;
    }
    items.get(activeSlot).shrink(match.get().value().getIngredientQuantity().count());
    activeSlot = -1;
    progress = duration = 0;
    pending = NonNullList.create();
    setChanged();
  }

  public Component getDisplayName() {
    return Component.translatable("block.create_bioships.stomach");
  }

  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new StomachMenu(id, inventory, this);
  }

  public ContainerData menuData() {
    return new ContainerData() {
      public int get(int index) {
        return switch (index) {
          case 0 -> acid;
          case 1 -> progress;
          case 2 -> duration;
          default -> 0;
        };
      }

      public void set(int index, int value) {}

      public int getCount() {
        return 3;
      }
    };
  }

  public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
    tooltip.add(Component.literal("Stomach acid: " + acid + " / " + MAX_ACID));
    tooltip.add(Component.literal("Digestion: " + progress + " / " + duration));
    tooltip.add(Component.literal("Queued food: " + queued() + " / 576"));
    return true;
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("Acid", acid);
    tag.putInt("Progress", progress);
    tag.putInt("Duration", duration);
    tag.putInt("ActiveSlot", activeSlot);
    tag.putInt("Eating", eating);
    tag.putString("Recipe", recipeId);
    var outputs = new CompoundTag();
    ContainerHelper.saveAllItems(outputs, pending, registries);
    outputs.putInt("Size", pending.size());
    tag.put("Pending", outputs);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    clientQueued = tag.getInt("Queued");
    acid = tag.contains("Acid") ? Math.clamp(tag.getInt("Acid"), 0, MAX_ACID) : 200;
    progress = Math.max(0, tag.getInt("Progress"));
    duration = Math.max(0, tag.getInt("Duration"));
    activeSlot = tag.contains("ActiveSlot") ? Math.clamp(tag.getInt("ActiveSlot"), -1, 8) : -1;
    eating = Math.clamp(tag.getInt("Eating"), 0, EAT_TICKS - 1);
    recipeId = tag.getString("Recipe");
    var outputs = tag.getCompound("Pending");
    pending = NonNullList.withSize(Math.clamp(outputs.getInt("Size"), 0, 32), ItemStack.EMPTY);
    ContainerHelper.loadAllItems(outputs, pending, registries);
  }
}
