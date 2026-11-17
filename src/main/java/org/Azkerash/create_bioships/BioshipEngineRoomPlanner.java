package org.Azkerash.create_bioships;

import static org.Azkerash.create_bioships.EngineRoomLayout.*;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Read-only engine-room clearance, load, capacity and complete-investment quotes. */
final class BioshipEngineRoomPlanner {
  record Quote(
      int columns,
      int pairs,
      Map<BlockPos, BlockState> changes,
      MaterialCost cost,
      int legUpgrades) {
    Quote {
      changes = Map.copyOf(changes);
    }

    MaterialCost completeCost(LivingCoreBlockEntity core) {
      int mouths = Math.max(0, pairs - DigestiveSystem.mouths(core).size());
      int stomachs = Math.max(0, pairs - DigestiveSystem.stomachs(core).size());
      return new MaterialCost(
          cost.flesh() + 8 * mouths + 16 * stomachs + 8 * legUpgrades,
          cost.fiber() + 4 * mouths + 8 * stomachs + 4 * legUpgrades,
          cost.bone() + 2 * mouths + 4 * stomachs + 2 * legUpgrades);
    }
  }

  static Quote quote(LivingCoreBlockEntity core) {
    return quote(core, reason -> {});
  }

  static String reason(LivingCoreBlockEntity core) {
    var reasons = new ArrayList<String>();
    return quote(core, reasons::add) != null
        ? "Ready"
        : reasons.isEmpty() ? "No room upgrade" : reasons.getFirst();
  }

  private static Quote blocked(java.util.function.Consumer<String> report, String reason) {
    report.accept(reason);
    return null;
  }

  private static Quote quote(
      LivingCoreBlockEntity core, java.util.function.Consumer<String> report) {
    int columns = core.engineRoom().columns();
    if (!AirshipPrototype.available()
        || !core.isMature()
        || core.blueprint().busy()
        || core.ship() == null
        || ComponentTransport.busy(core)
        || core.coreMotion().active()) return blocked(report, "Body is immature or moving");
    int desired =
        EngineRoomSizing.columns(
            Math.max(
                DigestiveSystem.requiredPairs(core),
                Math.max(
                    DigestiveSystem.mouths(core).size(), DigestiveSystem.stomachs(core).size())));
    if (desired <= columns || desired > 11)
      return blocked(report, "Room capacity already fits, or exceeds layout limit: " + desired);
    var level = core.getLevel();
    var before = new HashMap<BlockPos, BlockState>();
    for (var part : body(core.layoutVersion(), columns))
      if (part.offset().getZ() >= 0 && skin(part.state()))
        before.put(at(core, part.offset()), part.state());
    var after = new HashMap<BlockPos, BlockState>();
    // Added room tissue also consumes energy. Solve capacity before quoting the investment.
    int pairs = DigestiveSystem.requiredPairs(core);
    for (int pass = 0; pass < 11; pass++) {
      after.clear();
      for (var part : body(core.layoutVersion(), desired))
        if (part.offset().getZ() >= 0 && skin(part.state()))
          after.put(at(core, part.offset()), part.state());
      int added = Math.max(0, after.size() - before.size());
      pairs =
          Math.max(
              Math.max(DigestiveSystem.mouths(core).size(), DigestiveSystem.stomachs(core).size()),
              BlockEnergyTable.pairs(BlockEnergyTable.demand(BioshipEnergy.units(core) + added)));
      int next = Math.max(desired, EngineRoomSizing.columns(pairs));
      if (next == desired) break;
      if (next > 11) return blocked(report, "Added upkeep exceeds room layout limit");
      desired = next;
    }
    var changes = new LinkedHashMap<BlockPos, BlockState>();
    var cells = new HashSet<>(before.keySet());
    cells.addAll(after.keySet());
    int removed = 0, added = 0;
    double load = core.crawl().carriedLoad(core);
    for (var p : cells) {
      if (Objects.equals(before.get(p), after.get(p))) continue;
      var current = level.getBlockState(p);
      var wanted = after.getOrDefault(p, Blocks.AIR.defaultBlockState());
      if (wanted.equals(current)) continue;
      if (wanted.isAir()) {
        if (!skin(current)) continue;
        boolean eye = false;
        for (var face : Direction.values())
          if (level.getBlockState(p.relative(face)).is(Create_bioships.SURFACE_EYE.get()))
            eye = true;
        if (eye) continue;
        removed++;
      } else {
        if (current.is(Create_bioships.SURFACE_EYE.get())) {
          var direction = current.getValue(SurfaceEyeBlock.FACING);
          var destination = p.relative(direction);
          while (after.containsKey(destination)) destination = destination.relative(direction);
          var view = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(destination));
          if (!level.getBlockState(destination).isAir()
              || !BioshipIntegration.growthSpace(level, BlockPos.containing(view))
              || changes.containsKey(destination))
            return blocked(report, "Eye relocation blocked at " + destination);
          changes.put(destination, current);
          load += core.crawl().blockLoad(core, destination, current);
          added++;
        } else if (current.is(Create_bioships.INTESTINE.get()))
          wanted = current.setValue(IntestineBlock.ENCASED, true);
        else if (!current.isAir() && !skin(current))
          return blocked(report, "Existing component blocks expansion at " + p + ": " + current);
        if (current.isAir()
            || current.is(Create_bioships.INTESTINE.get())
                && !current.getValue(IntestineBlock.ENCASED)) added++;
        var physical = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(p));
        if (!BioshipIntegration.growthSpace(level, BlockPos.containing(physical))
            || !level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    new AABB(
                        physical.x - .5,
                        physical.y - .5,
                        physical.z - .5,
                        physical.x + .5,
                        physical.y + .5,
                        physical.z + .5),
                    e -> !(e instanceof BioshipTargetEntity))
                .isEmpty()) return blocked(report, "Terrain or occupant blocks expansion at " + p);
      }
      if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(p)))
        return blocked(report, "Expansion exceeds plot at " + p);
      load +=
          (wanted.isAir() ? 0 : core.crawl().blockLoad(core, p, wanted))
              - (current.isAir() ? 0 : core.crawl().blockLoad(core, p, current));
      changes.put(p, wanted);
    }
    int tiers = 0;
    while (!core.canFly()
        && core.crawl().legTier() + tiers < 3
        && load > BioshipCrawl.capacityFor(core.crawl().legTier() + tiers)) tiers++;
    if (!core.canFly() && load > BioshipCrawl.capacityFor(core.crawl().legTier() + tiers))
      return blocked(report, "Expanded room exceeds maximum leg support");
    int extra = desired - columns;
    return new Quote(
        desired,
        pairs,
        changes,
        new MaterialCost(Math.max(0, added - removed), 4 * extra, 2 * extra),
        tiers);
  }
}
