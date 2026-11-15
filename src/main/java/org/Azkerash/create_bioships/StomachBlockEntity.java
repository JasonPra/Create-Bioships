package org.Azkerash.create_bioships;

import com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity;
import com.github.elenterius.biomancy.crafting.recipe.DecomposingRecipe;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Outputs are tissue reserves, never an extractable inventory or capability. */
public class StomachBlockEntity extends BlockEntity implements MenuProvider {
  public static final int MAX_ACID = 1000;
  private final NonNullList<ItemStack> input = NonNullList.withSize(9, ItemStack.EMPTY);
  private final boolean[] inherited = new boolean[9];
  private final Map<ResourceLocation, Integer> reserves = new LinkedHashMap<>();
  final List<BlockPos> gutPath = new ArrayList<>();
  final Map<BlockPos, List<BlockPos>> sacPaths = new LinkedHashMap<>();
  BlockPos connectedMouth;
  boolean tubeBootstrapped;
  int freeTubeStock, paidTubeStock;
  private int acid = 200, progress, duration, activeSlot = -1, meals;
  private String activeRecipe = "";

  public StomachBlockEntity(BlockPos pos, BlockState state) {
    super(Create_bioships.STOMACH_ENTITY.get(), pos, state);
  }

  private int nutrientReserve;

  public int nutrients() {
    return nutrientReserve;
  }

  public void addNutrients(int amount) {
    nutrientReserve = Math.clamp(nutrientReserve + Math.max(0, amount), 0, 1000000);
    setChanged();
  }

  public int burnReserve() {
    if (nutrientReserve > 0) {
      nutrientReserve--;
      setChanged();
      return 20;
    }
    var data = new CompoundTag();
    reserves.forEach((id, count) -> data.putInt(id.toString(), count));
    int nutrition = burnStored(data);
    if (nutrition > 0) {
      reserves.clear();
      for (var key : data.getAllKeys()) reserves.put(ResourceLocation.parse(key), data.getInt(key));
      setChanged();
    }
    return nutrition;
  }

  static int burnStored(CompoundTag stores) {
    String[] priorities = {
      "biomancy:nutrients",
      "biomancy:nutrient_paste",
      "biomancy:nutrient_bar",
      "biomancy:flesh_bits",
      "biomancy:elastic_fibers",
      "biomancy:bone_fragments"
    };
    for (int i = 0; i < priorities.length; i++)
      if (stores.getInt(priorities[i]) > 0) {
        stores.putInt(priorities[i], stores.getInt(priorities[i]) - 1);
        return i < 3 ? 20 : i == 3 ? 10 : i == 4 ? 8 : 2;
      }
    for (var key : new java.util.TreeSet<>(stores.getAllKeys()))
      if (stores.getInt(key) > 0) {
        stores.putInt(key, stores.getInt(key) - 1);
        return 1;
      }
    return 0;
  }

  public int material(String id) {
    return reserves.getOrDefault(ResourceLocation.parse(id), 0);
  }

  public void retain(ItemStack stack) {
    if (!stack.isEmpty())
      reserves.merge(
          BuiltInRegistries.ITEM.getKey(stack.getItem()),
          stack.getCount(),
          (a, b) -> Math.min(1000000, a + b));
    setChanged();
  }

  public void retainFlesh(int count) {
    if (count > 0)
      reserves.merge(
          ResourceLocation.parse("biomancy:flesh_bits"), count, (a, b) -> Math.min(1000000, a + b));
    setChanged();
  }

  public boolean spend(String item, int count) {
    var id = ResourceLocation.parse(item);
    int stored = reserves.getOrDefault(id, 0);
    if (stored < count) return false;
    reserves.put(id, stored - count);
    setChanged();
    return true;
  }

  public int acid() {
    return acid;
  }

  public int meals() {
    return meals;
  }

  public void continueGrowth(LivingCoreBlockEntity core) {
    int amount = Math.min(core.missingBiomass(), material("biomancy:flesh_bits"));
    if (amount > 0) {
      spend("biomancy:flesh_bits", amount);
      core.incorporateBiomass(amount);
    }
  }

  public void inheritInput(ItemStack stack) {
    if (stack.isEmpty()) return;
    for (int slot = 0; slot < input.size(); slot++)
      if (input.get(slot).isEmpty()) {
        input.set(slot, stack.copy());
        inherited[slot] = true;
        setChanged();
        return;
      }
    retain(stack);
  }

  public int queuedFood() {
    int count = 0;
    for (var stack : input) count += stack.getCount();
    return count;
  }

  public boolean canEnqueue(ItemStack stack, boolean oldStock) {
    if (stack.isEmpty()
        || level
            .getRecipeManager()
            .getRecipeFor(
                DecomposerBlockEntity.RECIPE_TYPE.get(), new SingleRecipeInput(stack), level)
            .isEmpty()) return false;
    for (int slot = 0; slot < input.size(); slot++) {
      var current = input.get(slot);
      if (current.isEmpty()
          || inherited[slot] == oldStock
              && ItemStack.isSameItemSameComponents(current, stack)
              && current.getCount() < current.getMaxStackSize()) return true;
    }
    return false;
  }

  public boolean enqueue(ItemStack stack, boolean oldStock) {
    if (!canEnqueue(stack, oldStock)) return false;
    for (int slot = 0; slot < input.size(); slot++) {
      var current = input.get(slot);
      if (current.isEmpty()) {
        input.set(slot, stack.copyWithCount(1));
        inherited[slot] = oldStock;
        setChanged();
        return true;
      }
      if (inherited[slot] == oldStock
          && ItemStack.isSameItemSameComponents(current, stack)
          && current.getCount() < current.getMaxStackSize()) {
        current.grow(1);
        setChanged();
        return true;
      }
    }
    return false;
  }

  public boolean connected(LivingCoreBlockEntity core) {
    return DigestiveSystem.connected(core, this);
  }

  public static void tick(Level level, BlockPos pos, BlockState state, StomachBlockEntity stomach) {
    if (stomach.acid < MAX_ACID) {
      stomach.acid++;
      stomach.setChanged();
    }
    var ship = BioshipIntegration.containing(level, pos);
    if (ship == null) return;
    LivingCoreBlockEntity core = null;
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity living && living.primary(ship)) {
        core = living;
        break;
      }
    if (core == null || core.body().dead() || core.blueprint().busy() || !stomach.connected(core))
      return;
    stomach.continueGrowth(core);
    if (level.getGameTime() % 20 == 0) DigestiveSystem.pullStoredFood(core, stomach);
    java.util.List<Integer> order = new java.util.ArrayList<>();
    if (stomach.activeSlot >= 0 && !stomach.input.get(stomach.activeSlot).isEmpty())
      order.add(stomach.activeSlot);
    else {
      stomach.activeSlot = -1;
      for (int slot = 0; slot < stomach.input.size(); slot++) order.add(slot);
      final boolean hungry = core.hungry();
      order.sort(
          java.util.Comparator.comparing(
              slot ->
                  !(hungry
                      && !stomach.inherited[slot]
                      && LivingCoreBlockEntity.isMeat(stomach.input.get(slot)))));
    }
    for (int slot : order) {
      ItemStack stack = stomach.input.get(slot);
      if (stack.isEmpty()) continue;
      var recipe =
          level
              .getRecipeManager()
              .getRecipeFor(
                  DecomposerBlockEntity.RECIPE_TYPE.get(), new SingleRecipeInput(stack), level);
      if (recipe.isEmpty()) continue;
      DecomposingRecipe value = recipe.get().value();
      if (stack.getCount() < value.getIngredientQuantity().count()) continue;
      String key = recipe.get().id().toString();
      if (stomach.activeSlot != slot || !stomach.activeRecipe.equals(key)) {
        int cost = Math.clamp(value.getCraftingCostNutrients() * 100, 50, MAX_ACID);
        if (stomach.acid < cost) return;
        stomach.acid -= cost;
        stomach.progress = 0;
        stomach.activeSlot = slot;
        stomach.activeRecipe = key;
        stomach.duration = Math.max(1, value.getCraftingTimeTicks());
      }
      stomach.progress++;
      if (stomach.progress >= stomach.duration) {
        for (var output : value.getOutputs()) stomach.retain(output.getItemStack(level.random));
        if (!stomach.inherited[slot]) {
          core.digestMeal(stack);
          if (core.isMature()) stomach.meals++;
        }
        stack.shrink(value.getIngredientQuantity().count());
        stomach.progress = 0;
        stomach.activeSlot = -1;
        stomach.activeRecipe = "";
        stomach.continueGrowth(core);
      }
      stomach.setChanged();
      return;
    }
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.create_bioships.stomach");
  }

  @Override
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
          case 3 -> material("biomancy:flesh_bits");
          case 4 -> material("biomancy:bone_fragments");
          case 5 -> material("biomancy:elastic_fibers");
          case 6 -> queuedFood();
          case 7 -> sacPaths.size();
          case 8 -> nutrientReserve;
          default -> 0;
        };
      }

      public void set(int index, int value) {}

      public int getCount() {
        return 9;
      }
    };
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putBoolean("TubeBootstrapped", tubeBootstrapped);
    tag.putInt("FreeTubeStock", freeTubeStock);
    tag.putInt("PaidTubeStock", paidTubeStock);
    tag.putInt("NutrientReserve", nutrientReserve);
    net.minecraft.world.ContainerHelper.saveAllItems(tag, input, registries);
    tag.putInt("Acid", acid);
    tag.putInt("Progress", progress);
    tag.putInt("Duration", duration);
    tag.putInt("ActiveSlot", activeSlot);
    tag.putString("Recipe", activeRecipe);
    tag.putInt("Meals", meals);
    int flags = 0;
    for (int i = 0; i < 9; i++) if (inherited[i]) flags |= 1 << i;
    tag.putInt("InheritedInputs", flags);
    CompoundTag stores = new CompoundTag();
    reserves.forEach((key, value) -> stores.putInt(key.toString(), value));
    tag.put("TissueReserves", stores);
    ListTag path = new ListTag();
    for (var point : gutPath)
      path.add(new IntArrayTag(new int[] {point.getX(), point.getY(), point.getZ()}));
    tag.put("GutPath", path);
    ListTag branches = new ListTag();
    sacPaths.forEach(
        (endpoint, points) -> {
          CompoundTag branch = new CompoundTag();
          branch.putLong("Sac", endpoint.asLong());
          ListTag entries = new ListTag();
          for (var point : points)
            entries.add(new IntArrayTag(new int[] {point.getX(), point.getY(), point.getZ()}));
          branch.put("Path", entries);
          branches.add(branch);
        });
    tag.put("SacPaths", branches);
    if (connectedMouth != null) tag.putLong("ConnectedMouth", connectedMouth.asLong());
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    tubeBootstrapped =
        tag.contains("TubeBootstrapped")
            ? tag.getBoolean("TubeBootstrapped")
            : tag.contains("ConnectedMouth");
    freeTubeStock = Math.clamp(tag.getInt("FreeTubeStock"), 0, 1000000);
    paidTubeStock = Math.clamp(tag.getInt("PaidTubeStock"), 0, 1000000);
    nutrientReserve = Math.clamp(tag.getInt("NutrientReserve"), 0, 1000000);
    input.clear();
    net.minecraft.world.ContainerHelper.loadAllItems(tag, input, registries);
    acid = Math.clamp(tag.getInt("Acid"), 0, MAX_ACID);
    progress = Math.max(0, tag.getInt("Progress"));
    duration = Math.max(0, tag.getInt("Duration"));
    activeSlot = Math.clamp(tag.getInt("ActiveSlot"), -1, 8);
    activeRecipe = tag.getString("Recipe");
    meals = Math.max(0, tag.getInt("Meals"));
    for (int i = 0; i < 9; i++) inherited[i] = (tag.getInt("InheritedInputs") & (1 << i)) != 0;
    reserves.clear();
    CompoundTag stores = tag.getCompound("TissueReserves");
    for (String key : stores.getAllKeys())
      if (ResourceLocation.tryParse(key) != null)
        reserves.put(ResourceLocation.parse(key), Math.clamp(stores.getInt(key), 0, 1000000));
    gutPath.clear();
    for (Tag entry : tag.getList("GutPath", Tag.TAG_INT_ARRAY)) {
      int[] a = ((IntArrayTag) entry).getAsIntArray();
      if (a.length == 3) gutPath.add(new BlockPos(a[0], a[1], a[2]));
    }
    sacPaths.clear();
    for (Tag entry : tag.getList("SacPaths", Tag.TAG_COMPOUND)) {
      var branch = (CompoundTag) entry;
      List<BlockPos> points = new ArrayList<>();
      for (Tag node : branch.getList("Path", Tag.TAG_INT_ARRAY)) {
        int[] a = ((IntArrayTag) node).getAsIntArray();
        if (a.length == 3) points.add(new BlockPos(a[0], a[1], a[2]));
      }
      sacPaths.put(BlockPos.of(branch.getLong("Sac")), points);
    }
    connectedMouth =
        tag.contains("ConnectedMouth") ? BlockPos.of(tag.getLong("ConnectedMouth")) : null;
  }
}
