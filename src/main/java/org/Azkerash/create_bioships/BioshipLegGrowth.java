package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Paid locomotion sockets; attachment anatomy is separate from procedural foot placement. */
final class BioshipLegGrowth {
  static final MaterialCost COST = new MaterialCost(4, 2, 1);

  static int missing(LivingCoreBlockEntity core) {
    return Math.max(
        0,
        4
            - (int)
                BioshipStructure.cells(core.ship()).values().stream()
                    .filter(s -> BioshipLegEvolution.leg(s))
                    .count());
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    if (!DigestiveSystem.functional(core) || BioshipNutrition.critical(core) || missing(core) == 0)
      return null;
    int needed = missing(core);
    var completeCost =
        new MaterialCost(COST.flesh() * needed, COST.fiber() * needed, COST.bone() * needed);
    if (!completeCost.funded(BioshipStorage.account(core))
        || core.adaptation().starterLegBudget() == 0 && !EmergencyReserve.after(core, completeCost))
      return null;
    var candidates =
        sites(core, BioshipStructure.cells(core.ship())).stream()
            .filter(
                e ->
                    core.crawl().carriedLoad(core)
                            + core.crawl().blockLoad(core, e.getKey(), e.getValue())
                        <= core.crawl().loadCapacity())
            .toList();
    if (candidates.isEmpty()) return null;
    var next = candidates.getFirst();
    return new BioshipAdaptation.Proposal(
        BioshipBrain.Action.GROW_LEG,
        next.getKey(),
        next.getValue(),
        COST.flesh(),
        COST.fiber(),
        COST.bone(),
        985,
        "Grow four paid locomotion legs before vocal upgrades");
  }

  /** Geometric sites, independent of construction funding and future leg-strength choices. */
  static List<Map.Entry<BlockPos, BlockState>> sites(
      LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    var center =
        Vec3.atLowerCornerOf(core.structuralOrigin()).add(BioshipSupport.layout(core).center());
    var candidates = new ArrayList<Map.Entry<BlockPos, BlockState>>();
    for (var e : cells.entrySet()) {
      if (BioshipLegEvolution.leg(e.getValue())
          || e.getValue().is(Create_bioships.SURFACE_EYE.get())) continue;
      for (var face : Direction.Plane.HORIZONTAL) {
        if (!AmoebaLegBlock.canHostLeg(e.getValue(), e.getKey(), face)) continue;
        var pos = e.getKey().relative(face);
        if (cells.containsKey(pos)
            || !core.getLevel().getBlockState(pos).isAir()
            || !DigestiveSystem.permitsPlacement(core, pos)) continue;
        boolean mouthFace =
            cells.entrySet().stream()
                .anyMatch(
                    m ->
                        m.getValue().is(Create_bioships.FEEDING_MOUTH.get())
                            && m.getKey()
                                .relative(m.getValue().getValue(FeedingMouthBlock.FACING))
                                .equals(pos));
        if (mouthFace) continue;
        if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))) continue;
        var world =
            BlockPos.containing(core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos)));
        if (!BioshipIntegration.aquaticGrowthSpace(core.getLevel(), world)) continue;
        var state =
            Create_bioships.AMOEBA_LEG
                .get()
                .defaultBlockState()
                .setValue(AmoebaLegBlock.FACING, face);
        candidates.add(Map.entry(pos, state));
      }
    }
    // Spread sockets around the body rather than growing every leg on the first free face.
    candidates.sort(
        Comparator.<Map.Entry<BlockPos, BlockState>>comparingDouble(
                e -> {
                  var p = Vec3.atCenterOf(e.getKey());
                  double spacing =
                      cells.entrySet().stream()
                          .filter(c -> BioshipLegEvolution.leg(c.getValue()))
                          .mapToDouble(c -> Vec3.atCenterOf(c.getKey()).distanceToSqr(p))
                          .min()
                          .orElse(0);
                  return p.distanceToSqr(center) - spacing * 2;
                })
            .thenComparing(Map.Entry.comparingByKey()));
    return candidates;
  }

  static boolean develop(LivingCoreBlockEntity core, BlockPos pos, BlockState state) {
    var plan = proposal(core);
    if (plan == null || !plan.pos().equals(pos) || !plan.state().equals(state)) return false;
    var account = BioshipStorage.account(core);
    if (!COST.pay(account)) return false;
    if (!core.getLevel().setBlock(pos, state, 3)) {
      COST.refund(account);
      return false;
    }
    core.adaptation().usedStarterLeg();
    core.setChanged();
    return true;
  }

  static List<BioshipSupport.Leg> attachments(
      LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    var origin = Vec3.atLowerCornerOf(core.structuralOrigin());
    var result = new ArrayList<BioshipSupport.Leg>();
    cells.entrySet().stream()
        .filter(
            e ->
                BioshipLegEvolution.leg(e.getValue())
                    && AmoebaLegBlock.mounted(cells, e.getKey(), e.getValue()))
        .sorted(Map.Entry.comparingByKey())
        .limit(32)
        .forEach(
            e -> {
              var face = e.getValue().getValue(AmoebaLegBlock.FACING);
              var normal = Vec3.atLowerCornerOf(face.getNormal());
              var root = Vec3.atCenterOf(e.getKey()).subtract(origin).subtract(normal.scale(.48));
              var foot = root.add(normal.scale(1.3)).add(0, -1, 0);
              result.add(
                  new BioshipSupport.Leg(
                      root, foot, face, false, BioshipLegEvolution.swimming(e.getValue())));
            });
    return result;
  }
}
