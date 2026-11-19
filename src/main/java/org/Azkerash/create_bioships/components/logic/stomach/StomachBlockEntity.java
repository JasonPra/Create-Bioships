package org.Azkerash.create_bioships.components.logic.stomach;

import com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity;
import com.github.elenterius.biomancy.crafting.recipe.DecomposingRecipe;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.inventory.*;
import org.Azkerash.create_bioships.core.*;

/**
 * Inventory/menu view of sublevel-owned stomach state. Intestines deliver input; players inspect
 * but cannot insert.
 */
public final class StomachBlockEntity extends OrganInventory implements IHaveGoggleInformation {
  @Override
  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = new CompoundTag();
    tag.putInt("Acid", state().acid);
    tag.put("Genes", genes.save());
    tag.putInt("Progress", state().progress);
    tag.putInt("Duration", state().duration);
    tag.putInt("Queued", queued());
    return tag;
  }

  @Override
  public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
    state().acid = tag.getInt("Acid");
    state().progress = tag.getInt("Progress");
    state().duration = tag.getInt("Duration");
    clientQueued = tag.getInt("Queued");
    genes.load(tag.getCompound("Genes"));
  }

  private final StomachGenes genes = new StomachGenes(this);

  public StomachGenes genes() {
    return genes;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, worldPosition);
      if (body != null) SubLevelHunger.refreshCapacity(body);
    }
  }

  private int clientQueued;
  public static final int INPUT_SLOTS = 9,
      MAX_ACID = 1000,
      EAT_TICKS = 32,
      PASTE_PER_MEAL = 1,
      HUNGER_PER_MEAL = 4;
  private final StomachState detachedState = new StomachState();

  @Override
  protected org.Azkerash.create_bioships.core.component.ComponentState localState() {
    return detachedState;
  }

  public StomachState state() {
    return (StomachState) ownedState();
  }

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
    return state().acid;
  }

  public int progress() {
    return state().progress;
  }

  public int duration() {
    return state().duration;
  }

  public int queued() {
    if (level != null && level.isClientSide) return clientQueued;
    return items().subList(0, INPUT_SLOTS).stream().mapToInt(ItemStack::getCount).sum();
  }

  public static Item paste() {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:nutrient_paste"));
  }

  public void advance() {
    StomachDigestion.advance(this);
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
          case 0 -> state().acid;
          case 1 -> state().progress;
          case 2 -> state().duration;
          case 3 -> genes.longevityTier();
          case 4 -> genes.efficiency();
          default -> 0;
        };
      }

      public void set(int index, int value) {}

      public int getCount() {
        return 5;
      }
    };
  }

  public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
    tooltip.add(Component.literal("Stomach acid: " + state().acid + " / " + MAX_ACID));
    tooltip.add(Component.literal("Digestion: " + state().progress + " / " + state().duration));
    tooltip.add(Component.literal("Queued food: " + queued() + " / 576"));
    tooltip.add(
        Component.literal(
            "Longevity gene: " + genes.longevityTier() + " / " + StomachGenes.MAX_LONGEVITY));
    tooltip.add(Component.literal("Efficiency gene: " + genes.efficiency() + " / 3"));
    return true;
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.merge(state().save(registries));
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    clientQueued = tag.getInt("Queued");
    state().load(tag, registries);
    if (level instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, worldPosition);
      if (body != null) SubLevelHunger.refreshCapacity(body);
    }
  }
}
