package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Breathing capability and resource-funded proposals; habitat sampling stays in the aquatic sensor.
 */
final class BioshipRespiration {
  static final MaterialCost LUNG_COST = new MaterialCost(6, 3, 0),
      GILL_COST = new MaterialCost(6, 6, 0);

  static int bodySize(LivingCoreBlockEntity core) {
    return bodySize(BioshipStructure.cells(core.ship()));
  }

  static int bodySize(Map<BlockPos, BlockState> cells) {
    return (int)
        cells.values().stream()
            .filter(
                s ->
                    !BioshipLegEvolution.leg(s)
                        && !s.is(Create_bioships.SURFACE_EYE.get())
                        && (!s.is(Create_bioships.INTESTINE.get())
                            || s.getValue(IntestineBlock.ENCASED)))
            .count();
  }

  static boolean has(LivingCoreBlockEntity core, boolean gills) {
    return BioshipStructure.cells(core.ship()).values().stream()
        .anyMatch(s -> s.is(gills ? Create_bioships.GILLS.get() : Create_bioships.LUNGS.get()));
  }

  static boolean airAccess(LivingCoreBlockEntity core) {
    var mouths = DigestiveSystem.mouths(core);
    return mouths.isEmpty()
        ? BioshipAquatic.aboveFluid(core, BioshipBlueprint.physical(core, BlockPos.ZERO))
        : mouths.stream()
            .anyMatch(
                p ->
                    BioshipAquatic.aboveFluid(
                        core, core.ship().logicalPose().transformPosition(Vec3.atCenterOf(p))));
  }

  static boolean exposedGill(LivingCoreBlockEntity core, BlockPos pos, BlockState state) {
    var face = state.getValue(RespiratoryOrganBlock.FACING);
    var outside = pos.relative(face);
    if (!core.getLevel().getBlockState(outside).isAir()) return false;
    var center = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
    var surface =
        core.ship()
            .logicalPose()
            .transformPosition(
                Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.55)));
    return !BioshipAquatic.aboveFluid(core, center) && !BioshipAquatic.aboveFluid(core, surface);
  }

  static boolean activeGills(LivingCoreBlockEntity core) {
    return BioshipStructure.cells(core.ship()).entrySet().stream()
        .anyMatch(
            e ->
                e.getValue().is(Create_bioships.GILLS.get())
                    && exposedGill(core, e.getKey(), e.getValue()));
  }

  static boolean canBreathe(LivingCoreBlockEntity core) {
    return activeGills(core) || airAccess(core) && (bodySize(core) <= 4 || has(core, false));
  }

  static boolean wantsGills(LivingCoreBlockEntity core) {
    return core.aquatic().gillUsage().samples() >= 40 && core.aquatic().gillUsage().score() >= 650
        || core.aquatic().waterContact() && core.aquatic().air() < 200 && !canBreathe(core);
  }

  static boolean prefersGills(LivingCoreBlockEntity core) {
    return core.aquatic().waterContact() || wantsGills(core);
  }

  static boolean missingLungs(LivingCoreBlockEntity core) {
    return bodySize(core) > 4 && !has(core, false);
  }

  static boolean needsLungs(LivingCoreBlockEntity core) {
    return missingLungs(core) && !prefersGills(core);
  }

  static boolean blocksOpening(LivingCoreBlockEntity core, BlockPos target) {
    return BioshipStructure.cells(core.ship()).entrySet().stream()
        .anyMatch(
            e ->
                e.getValue().is(Create_bioships.GILLS.get())
                    && e.getKey()
                        .relative(e.getValue().getValue(RespiratoryOrganBlock.FACING))
                        .equals(target));
  }

  record Growth(BlockPos pos, BlockState state, MaterialCost cost, boolean gills) {}

  static Growth plan(LivingCoreBlockEntity core) {
    if (core.ship() == null || core.body().dead()) return null;
    boolean gills = wantsGills(core) && !has(core, true);
    boolean lung = !gills && !prefersGills(core) && missingLungs(core) && airAccess(core);
    if (!lung && !gills) return null;
    var cells = BioshipStructure.cells(core.ship());
    var choices = new LinkedHashMap<BlockPos, BlockState>();
    for (var host : cells.keySet())
      for (var face : Direction.values()) {
        var pos = host.relative(face);
        if (!core.getLevel().getBlockState(pos).isAir()
            || !DigestiveSystem.permitsPlacement(core, pos)) continue;
        if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))) continue;
        var state =
            (gills ? Create_bioships.GILLS.get() : Create_bioships.LUNGS.get())
                .defaultBlockState()
                .setValue(RespiratoryOrganBlock.FACING, face);
        if (gills && !exposedGill(core, pos, state)) continue;
        var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
        if (!BioshipIntegration.aquaticGrowthSpace(core.getLevel(), BlockPos.containing(world))
            || !BioshipSupport.safeAddition(core, pos, state)) continue;
        choices.put(pos, state);
      }
    var pos =
        choices.keySet().stream()
            .min(Comparator.comparingDouble(p -> p.distSqr(core.getBlockPos())))
            .orElse(null);
    return pos == null
        ? null
        : new Growth(pos, choices.get(pos), gills ? GILL_COST : LUNG_COST, gills);
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    var plan = plan(core);
    if (plan == null || !plan.cost().funded(BioshipStorage.account(core))) return null;
    // Lungs are essential life support; gills are a sustained-use investment and retain the
    // reserve.
    if (plan.gills() && canBreathe(core) && !EmergencyReserve.after(core, plan.cost())) return null;
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.GROW_RESPIRATION,
        plan.pos(),
        plan.state(),
        plan.cost().flesh(),
        plan.cost().fiber(),
        0,
        plan.gills() && canBreathe(core) ? 1040 : 1160,
        plan.gills()
            ? "Grow exposed gills after sustained submersion"
            : "Grow lungs to support a body larger than four blocks");
  }

  static boolean grow(LivingCoreBlockEntity core, BlockPos pos, BlockState state) {
    var quote = proposal(core);
    if (quote == null || !quote.pos().equals(pos) || !quote.state().equals(state)) return false;
    var cost = new MaterialCost(quote.flesh(), quote.fiber(), quote.bone());
    var account = BioshipStorage.account(core);
    if (!cost.pay(account)) return false;
    if (!core.getLevel().setBlock(pos, state, 3)) {
      cost.refund(account);
      return false;
    }
    BioshipAnatomy.invalidate(core);
    core.setChanged();
    return true;
  }
}
