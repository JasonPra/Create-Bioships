package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Paid mounting tissue; quotes the remaining support, leg, strength and breathing investment. */
final class BioshipMountGrowth {
  static final int URGENCY = 990;

  record Plan(BlockPos pos, BlockState state, int legUpgrades, MaterialCost completeCost) {}

  private record Demand(long at, MaterialCost cost) {}

  private static final Map<LivingCoreBlockEntity, Demand> DEMAND = new WeakHashMap<>();

  static MaterialCost demand(LivingCoreBlockEntity core) {
    if (core.getLevel() == null || core.ship() == null) return new MaterialCost(0, 0, 0);
    var old = DEMAND.get(core);
    long now = core.getLevel().getGameTime();
    if (old != null && now - old.at() < 20) return old.cost();
    var plan = plan(core);
    var cost = plan == null ? new MaterialCost(0, 0, 0) : plan.completeCost();
    DEMAND.put(core, new Demand(now, cost));
    return cost;
  }

  private record Step(BlockPos pos, BlockState state, double score) {}

  static Plan plan(LivingCoreBlockEntity core) {
    if (!BioshipBodyBehavior.blocker(core).isEmpty()
        || !DigestiveSystem.functional(core)
        || BioshipNutrition.critical(core)) return null;
    var original = BioshipStructure.cells(core.ship());
    int missing = BioshipLegGrowth.missing(core);
    boolean invalid =
        original.entrySet().stream()
            .anyMatch(
                e ->
                    BioshipLegEvolution.leg(e.getValue())
                        && !AmoebaLegBlock.mounted(original, e.getKey(), e.getValue()));
    if (missing == 0 && !invalid || !BioshipLegGrowth.sites(core, original).isEmpty()) return null;
    var future = new LinkedHashMap<>(original);
    var steps = new ArrayList<Step>();
    var center =
        Vec3.atLowerCornerOf(core.structuralOrigin()).add(BioshipSupport.layout(core).center());
    boolean lungs;
    while (true) {
      lungs =
          BioshipRespiration.bodySize(future) > 4
              && !BioshipRespiration.has(core, BioshipRespiration.prefersGills(core));
      int required = Math.max(missing, invalid ? 1 : 0) + (lungs ? 1 : 0);
      int available = siteCount(core, future);
      if (available >= required) break;
      if (steps.size() == 4) return null;
      var choices = new LinkedHashMap<BlockPos, BlockState>();
      for (var e : future.entrySet()) {
        if (e.getValue().is(Create_bioships.INTESTINE.get())
            && !e.getValue().getValue(IntestineBlock.ENCASED))
          choices.put(e.getKey(), e.getValue().setValue(IntestineBlock.ENCASED, true));
        if (BioshipLegEvolution.leg(e.getValue())
            || e.getValue().is(Create_bioships.SURFACE_EYE.get())) continue;
        for (var face : Direction.values()) {
          var pos = e.getKey().relative(face);
          if (!future.containsKey(pos))
            choices.putIfAbsent(pos, BioshipTissue.body().defaultBlockState());
        }
      }
      var ranked =
          choices.entrySet().stream()
              .sorted(
                  Comparator.comparingDouble(
                      e ->
                          (future.containsKey(e.getKey()) ? 0 : 100)
                              + e.getKey().distToCenterSqr(center)))
              .limit(96)
              .toList();
      Step best = null;
      for (var e : ranked) {
        var pos = e.getKey();
        var state = e.getValue();
        boolean encasing = state.is(Create_bioships.INTESTINE.get());
        if (pos.getY() > center.y + .5
            || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))
            || BioshipAnatomy.mouthOpening(core, pos)
            || BioshipRespiration.blocksOpening(core, pos)
            || !encasing
                && (!core.getLevel().getBlockState(pos).isAir()
                    || !DigestiveSystem.permitsPlacement(core, pos))) continue;
        var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
        if (!BioshipIntegration.aquaticGrowthSpace(core.getLevel(), BlockPos.containing(world))
            || !core.getLevel()
                .getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    new AABB(
                        world.x - .5,
                        world.y - .5,
                        world.z - .5,
                        world.x + .5,
                        world.y + .5,
                        world.z + .5),
                    entity -> !(entity instanceof BioshipTargetEntity))
                .isEmpty()) continue;
        var proposed = new LinkedHashMap<>(future);
        proposed.put(pos, state);
        if (!BioshipSupport.layout(core, proposed).stable()) continue;
        int gain = siteCount(core, proposed) - available;
        if (gain <= 0) continue;
        double score =
            (encasing ? 0 : 100)
                + pos.distToCenterSqr(center)
                + Math.max(0, pos.getY() - core.structuralOrigin().getY()) * 4
                - gain * 20;
        if (best == null || score < best.score()) best = new Step(pos, state, score);
      }
      if (best == null) return null;
      steps.add(best);
      future.put(best.pos(), best.state());
    }
    double load = core.crawl().carriedLoad(core);
    for (var step : steps) {
      load += core.crawl().blockLoad(core, step.pos(), step.state());
      if (original.containsKey(step.pos()))
        load -= core.crawl().blockLoad(core, step.pos(), original.get(step.pos()));
    }
    load +=
        missing
            * core.crawl()
                .blockLoad(
                    core, core.getBlockPos(), Create_bioships.AMOEBA_LEG.get().defaultBlockState());
    if (lungs)
      load +=
          core.crawl()
              .blockLoad(
                  core,
                  core.getBlockPos(),
                  (BioshipRespiration.prefersGills(core)
                          ? Create_bioships.GILLS.get()
                          : Create_bioships.LUNGS.get())
                      .defaultBlockState());
    int upgrades = 0;
    while (core.crawl().legTier() + upgrades < 3
        && load > BioshipCrawl.capacityFor(core.crawl().legTier() + upgrades)) upgrades++;
    if (load > BioshipCrawl.capacityFor(core.crawl().legTier() + upgrades)) return null;
    var support = BioshipTissue.GROWTH_COST;
    var leg = BioshipLegGrowth.COST;
    var breath =
        lungs
            ? (BioshipRespiration.prefersGills(core)
                ? BioshipRespiration.GILL_COST
                : BioshipRespiration.LUNG_COST)
            : new MaterialCost(0, 0, 0);
    var complete =
        new MaterialCost(
            support.flesh() * steps.size() + leg.flesh() * missing + 8 * upgrades + breath.flesh(),
            support.fiber() * steps.size() + leg.fiber() * missing + 4 * upgrades + breath.fiber(),
            support.bone() * steps.size() + leg.bone() * missing + 2 * upgrades + breath.bone());
    var first = steps.getFirst();
    return new Plan(first.pos(), first.state(), upgrades, complete);
  }

  private static int siteCount(LivingCoreBlockEntity core, Map<BlockPos, BlockState> cells) {
    return (int)
        BioshipLegGrowth.sites(core, cells).stream().map(Map.Entry::getKey).distinct().count();
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core) {
    return proposal(core, plan(core));
  }

  static BioshipAdaptation.Proposal proposal(LivingCoreBlockEntity core, Plan plan) {
    if (plan == null || !EmergencyReserve.after(core, plan.completeCost())) return null;
    var price = BioshipTissue.GROWTH_COST;
    return new BioshipAdaptation.Proposal(
        plan.legUpgrades() > 0
            ? BioshipBrain.Action.STRENGTHEN_LEGS
            : BioshipBrain.Action.GROW_SUPPORT,
        plan.pos(),
        plan.state(),
        price.flesh(),
        price.fiber(),
        price.bone(),
        URGENCY,
        plan.legUpgrades() > 0
            ? "Strengthen legs before adding a fully budgeted mounting face"
            : "Grow paid mounting tissue before more locomotion legs");
  }

  static boolean grow(LivingCoreBlockEntity core, BlockPos pos, BlockState state) {
    var plan = plan(core);
    if (plan == null
        || plan.legUpgrades() > 0
        || !plan.pos().equals(pos)
        || !plan.state().equals(state)
        || !EmergencyReserve.after(core, plan.completeCost())) return false;
    var price = BioshipTissue.GROWTH_COST;
    var account = BioshipStorage.account(core);
    if (!price.pay(account)) return false;
    if (!core.getLevel().setBlock(pos, state, 3)) {
      price.refund(account);
      return false;
    }
    DEMAND.remove(core);
    BioshipAnatomy.invalidate(core);
    BioshipDigestiveRepair.invalidate(core);
    core.setChanged();
    return true;
  }

  private BioshipMountGrowth() {}
}
