package org.Azkerash.create_bioships.amoeba.merging;

import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.amoeba.components.AmoebaComponentHost;

import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.FeedingMouthBlockEntity;
import org.Azkerash.create_bioships.StomachBlockEntity;

import com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity;
import com.github.elenterius.biomancy.block.digester.DigesterBlockEntity;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.*;

/** Recipes and commit validation for directional-independent amoeba component fusion. */
public final class ComponentMerging {
  private ComponentMerging() {}

  public static final int DURATION_TICKS = 36;

  /** Pure animation math stays independently testable without loading Minecraft classes. */
  public static final class Animation {
    private Animation() {}

    public static double scale(double progress, boolean swallowed) {
      double t = Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
      double edge = swallowed ? (1 - t) / .25 : Math.min(t, 1 - t) / .2;
      edge = Math.clamp(edge, 0, 1);
      double smooth = edge * edge * (3 - 2 * edge);
      return swallowed ? .025 + .975 * smooth : 1 - .975 * smooth;
    }
  }

  @FunctionalInterface
  public interface Commit {
    void apply(AmoebaComponentHost core, BlockEntity source, BlockEntity destination);
  }

  public record Recipe(
      String id,
      Class<? extends BlockEntity> source,
      Class<? extends BlockEntity> destination,
      Supplier<BlockState> result,
      Predicate<AmoebaComponentHost> needed,
      Commit commit) {
    boolean matches(BlockEntity from, BlockEntity to) {
      return source.isInstance(from) && destination.isInstance(to);
    }
  }

  private static final class Registry {
    private static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
      register(
          new Recipe(
              "stomach",
              DigesterBlockEntity.class,
              DecomposerBlockEntity.class,
              () -> Create_bioships.STOMACH.get().defaultBlockState(),
              core -> core.needsStomach(),
              (core, source, destination) ->
                  finishStomach(
                      core, (DecomposerBlockEntity) destination, (DigesterBlockEntity) source)));
    }
  }

  public static void register(Recipe recipe) {
    if (Registry.RECIPES.putIfAbsent(recipe.id(), recipe) != null)
      throw new IllegalArgumentException("Duplicate component merge recipe: " + recipe.id());
  }

  public static boolean matches(String id, BlockEntity source, BlockEntity destination) {
    var recipe = Registry.RECIPES.get(id);
    return recipe != null && recipe.matches(source, destination);
  }

  public static BlockState result(String id) {
    return Registry.RECIPES.get(id).result().get();
  }

  private static boolean commit(
      String id, AmoebaComponentHost core, BlockEntity source, BlockEntity destination) {
    var recipe = Registry.RECIPES.get(id);
    if (recipe == null || !recipe.matches(source, destination)) return false;
    recipe.commit().apply(core, source, destination);
    return true;
  }

  public static void tryAvailable(AmoebaComponentHost core) {
    if (core.body() == null || ComponentTransport.busy(core)) return;
    var entities = core.components();
    for (var recipe : Registry.RECIPES.values()) {
      if (!recipe.needed().test(core)) continue;
      for (var destination : entities)
        for (var source : entities) {
          if (source == destination || !recipe.matches(source, destination)) continue;
          // Connected tissue conveys the parts: no empty staging cell above the destination is
          // needed.
          if (ComponentTransport.merge(
              core, source.getBlockPos(), destination.getBlockPos(), recipe.id())) return;
        }
    }
  }

  public static boolean absorbLarynx(AmoebaComponentHost core, CompoundTag capsule) {
    if (!core.digestionReady()) return false;
    var mouth = core.mouths().stream().filter(m -> !m.voiced()).findFirst().orElse(null);
    if (mouth == null) return false;
    attachLarynx(core, mouth, capsule);
    return true;
  }

  public static void attachLarynx(
      AmoebaComponentHost core, FeedingMouthBlockEntity mouth, CompoundTag capsule) {
    mouth.absorbLarynx(capsule);
    core.changed();
  }

  private static final String RECIPE_TAG = "BioshipMergeRecipe";

  public static void tag(CompoundTag data, String recipe) {
    data.putString(RECIPE_TAG, recipe);
  }

  /** Returns false only for cargo which is not a fusion transaction. */
  public static boolean finish(
      AmoebaComponentHost core,
      dev.ryanhcode.sable.sublevel.ServerSubLevel cargo,
      ComponentTransport.Move move) {
    var tag = cargo.getUserDataTag();
    String recipe = tag == null ? "" : tag.getString(RECIPE_TAG);
    if (recipe.isEmpty() && tag != null && tag.getBoolean("BioshipCombineStomach"))
      recipe = "stomach";
    if (recipe.isEmpty()) return false;
    var owner = core.body();
    if (!commit(
        recipe,
        core,
        core.component(cargo, move.cargoBlock()),
        core.component(owner, move.target()))) {
      ComponentTransport.release(core, cargo);
      return true;
    }
    ComponentTransport.finishCargo(core, cargo);
    core.merged();
    return true;
  }

  private static void finishStomach(
      AmoebaComponentHost core,
      com.github.elenterius.biomancy.block.decomposer.DecomposerBlockEntity decomposer,
      com.github.elenterius.biomancy.block.digester.DigesterBlockEntity digester) {
    var destination = decomposer.getBlockPos();
    var queued = new ArrayList<net.minecraft.world.item.ItemStack>();
    var stores = new ArrayList<net.minecraft.world.item.ItemStack>();
    copyItems(decomposer.getInputInventory(), queued);
    copyItems(digester.getInputInventory(), queued);
    copyItems(decomposer.getOutputInventory(), stores);
    copyItems(digester.getOutputInventory(), stores);
    stores.add(decomposer.getStackInFuelSlot().copy());
    stores.add(digester.getStackInFuelSlot().copy());
    int nutrients =
        decomposer.getStateData().fuelHandler.getFuelAmount()
            + digester.getStateData().fuelHandler.getFuelAmount();
    var stomachHost = core.hostTissue(decomposer);
    core.level().removeBlockEntity(destination);
    core.level().setBlock(destination, Create_bioships.STOMACH.get().defaultBlockState(), 3);
    if (core.component(core.body(), destination) instanceof StomachBlockEntity stomach) {
      core.rememberHost(stomach, stomachHost);
      core.stomachFormed();
      for (var stack : stores) stomach.retain(stack);
      for (var stack : queued) stomach.inheritInput(stack);
      stomach.addNutrients(nutrients);
    }
  }

  private static void copyItems(
      net.neoforged.neoforge.items.IItemHandler inventory,
      List<net.minecraft.world.item.ItemStack> destination) {
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      destination.add(inventory.getStackInSlot(slot).copy());
  }
}
