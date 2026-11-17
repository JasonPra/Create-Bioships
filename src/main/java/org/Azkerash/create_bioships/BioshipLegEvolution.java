package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Usage-based, paid specialization of existing legs; does not change mouth graspers. */
public final class BioshipLegEvolution {
  static final MaterialCost SWIM_COST = new MaterialCost(4, 4, 0),
      LAND_COST = new MaterialCost(2, 2, 0);

  public static boolean leg(BlockState state) {
    return state.getBlock() instanceof AmoebaLegBlock;
  }

  static boolean swimming(BlockState state) {
    return state.is(Create_bioships.SWIMMING_LEG.get());
  }

  static List<Map.Entry<BlockPos, BlockState>> legs(LivingCoreBlockEntity core) {
    return BioshipStructure.cells(core.ship()).entrySet().stream()
        .filter(e -> leg(e.getValue()))
        .sorted(Map.Entry.comparingByKey())
        .toList();
  }

  static double swimmingFraction(LivingCoreBlockEntity core) {
    var legs = legs(core);
    return legs.isEmpty()
        ? 0
        : legs.stream().filter(e -> swimming(e.getValue())).count() / (double) legs.size();
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    if (!DigestiveSystem.functional(core)
        || BioshipNutrition.critical(core)
        || core.getLevel().getGameTime() < core.aquatic().nextLegEvolution()) return null;
    var legs = legs(core);
    if (legs.size() < 4) return null;
    int current = (int) legs.stream().filter(e -> swimming(e.getValue())).count();
    int wanted = core.aquatic().legUsage().desired(legs.size(), current);
    if (wanted == current) return null;
    boolean swim = wanted > current;
    var cost = swim ? SWIM_COST : LAND_COST;
    if (!EmergencyReserve.after(core, cost)) return null;
    var source =
        legs.stream().filter(e -> swimming(e.getValue()) != swim).findFirst().orElseThrow();
    var state =
        (swim ? Create_bioships.SWIMMING_LEG.get() : Create_bioships.AMOEBA_LEG.get())
            .defaultBlockState()
            .setValue(AmoebaLegBlock.FACING, source.getValue().getValue(AmoebaLegBlock.FACING));
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.EVOLVE_LEGS,
        source.getKey(),
        state,
        cost.flesh(),
        cost.fiber(),
        cost.bone(),
        swim ? 1030 : 700,
        swim
            ? "Evolve a paddle leg after sustained swimming use"
            : "Restore a walking leg after sustained land use");
  }

  static boolean evolve(LivingCoreBlockEntity core, BlockPos pos, BlockState state) {
    var quote = proposal(core);
    if (quote == null || !quote.pos().equals(pos) || !quote.state().equals(state)) return false;
    var cost = new MaterialCost(quote.flesh(), quote.fiber(), quote.bone());
    var account = BioshipStorage.account(core);
    if (!cost.pay(account)) return false;
    if (!core.getLevel().setBlock(pos, state, 3)) {
      cost.refund(account);
      return false;
    }
    core.aquatic().legEvolved(core.getLevel().getGameTime() + 200);
    BioshipAnatomy.invalidate(core);
    core.setChanged();
    return true;
  }
}
