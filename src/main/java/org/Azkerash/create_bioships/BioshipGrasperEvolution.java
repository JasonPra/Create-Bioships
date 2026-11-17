package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;

/** Mouth-owned swimming graspers share the leg specialization budget, history and cooldown. */
final class BioshipGrasperEvolution {
  record Change(FeedingMouthBlockEntity mouth, int ordinal, boolean swimming, MaterialCost cost) {}

  static Change plan(LivingCoreBlockEntity core) {
    if (!DigestiveSystem.functional(core)
        || BioshipNutrition.critical(core)
        || core.getLevel().getGameTime() < core.aquatic().nextLegEvolution()) return null;
    var slots = BioshipGraspers.slots(core).stream().filter(s -> !s.key().equals("core")).toList();
    int current = (int) slots.stream().filter(BioshipGraspers.Slot::swimming).count();
    int desired = core.aquatic().legUsage().desired(slots.size(), current);
    if (current == desired) return null;
    boolean swim = desired > current;
    var slot = slots.stream().filter(s -> s.swimming() != swim).findFirst().orElse(null);
    if (slot == null
        || !(core.getLevel().getBlockEntity(slot.mouth()) instanceof FeedingMouthBlockEntity mouth))
      return null;
    var cost = swim ? BioshipLegEvolution.SWIM_COST : BioshipLegEvolution.LAND_COST;
    return EmergencyReserve.after(core, cost)
        ? new Change(mouth, slot.ordinal(), swim, cost)
        : null;
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    var change = plan(core);
    if (change == null) return null;
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.EVOLVE_GRASPERS,
        change.mouth().getBlockPos(),
        change.mouth().getBlockState(),
        change.cost().flesh(),
        change.cost().fiber(),
        change.cost().bone(),
        change.swimming() ? 1030 : 700,
        change.swimming()
            ? "Adapt a hunting grasper for frequent underwater use"
            : "Restore a hunting grasper after sustained land use");
  }

  static boolean evolve(LivingCoreBlockEntity core, BlockPos pos) {
    var change = plan(core);
    if (change == null || !change.mouth().getBlockPos().equals(pos)) return false;
    if (!change.cost().pay(BioshipStorage.account(core))) return false;
    change.mouth().swimmingGrasper(change.ordinal(), change.swimming());
    core.aquatic().legEvolved(core.getLevel().getGameTime() + 200);
    core.setChanged();
    return true;
  }
}
