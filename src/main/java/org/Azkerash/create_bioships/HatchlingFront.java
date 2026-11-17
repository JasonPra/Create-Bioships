package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;

/** Logical north is the hatchling's front; facing the target rotates the living body itself. */
final class HatchlingFront {
  static BlockPos port(LivingCoreBlockEntity core, BlockPos source) {
    var origin = core.structuralOrigin();
    var cells = new HashSet<>(BioshipDecay.blocks(core.ship()));
    var candidates = new ArrayList<BlockPos>();
    for (var p : cells) {
      if (p.getY() != origin.getY() || p.equals(source)) continue;
      var tissue = core.getLevel().getBlockState(p);
      if (!p.equals(core.getBlockPos())
          && !p.equals(origin)
          && !BioshipTissue.isTissue(tissue)
          && !tissue.is(Create_bioships.CARAPACE.get())) continue;
      var north = p.north();
      if (BioshipTissue.isTissue(core.getLevel().getBlockState(p))) candidates.add(p);
      if (core.getLevel().getBlockState(north).isAir()) candidates.add(north);
    }
    if (source != null) candidates.add(source);
    candidates.sort(
        Comparator.<BlockPos>comparingInt(BlockPos::getZ)
            .thenComparingInt(p -> Math.abs(p.getX() - origin.getX())));
    for (var p : candidates) {
      if (p.distManhattan(origin) > 6 || !FeedingMouthBlock.openFace(core, p, Direction.NORTH))
        continue;
      var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(p));
      if (!core.getLevel().getBlockState(BlockPos.containing(world)).canBeReplaced()) continue;
      if (source != null && p.equals(source)) return p;
      var state = core.getLevel().getBlockState(p);
      if (!state.isAir() && !BioshipTissue.isTissue(state)) continue;
      if (source != null && state.isAir() && !connectedAfter(cells, source, p)) continue;
      return p;
    }
    return source;
  }

  private static boolean connectedAfter(Set<BlockPos> body, BlockPos source, BlockPos target) {
    var cells = new HashSet<>(body);
    cells.remove(source);
    cells.add(target);
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    queue.add(target);
    seen.add(target);
    while (!queue.isEmpty()) {
      var point = queue.removeFirst();
      for (var d : Direction.values()) {
        var next = point.relative(d);
        if (cells.contains(next) && seen.add(next)) queue.add(next);
      }
    }
    return seen.size() == cells.size();
  }

  static boolean canArrange(LivingCoreBlockEntity core) {
    var source = core.mouthBlock();
    if (source == null
        || ComponentTransport.busy(core.amoebaComponents())
        || core.coreMotion().active()) return false;
    var state = core.getLevel().getBlockState(source);
    if (!state.is(Create_bioships.FEEDING_MOUTH.get())) return false;
    var target = port(core, source);
    var facing =
        source.equals(target) && FeedingMouthBlock.openFace(core, source, Direction.NORTH)
            ? Direction.NORTH
            : FeedingMouthBlock.outward(core, source);
    if (state.getValue(FeedingMouthBlock.FACING) != facing) return true;
    if (target == null || target.equals(source) || !DigestiveSystem.permitsPlacement(core, target))
      return false;
    return BioshipSupport.safeMove(
            core,
            source,
            target,
            !BioshipTissue.host(core.getLevel().getBlockEntity(source)).isAir())
        && DigestiveSystem.reserveOrganRoute(core, source, target, false);
  }

  static void arrangeMouth(LivingCoreBlockEntity core) {
    var source = core.mouthBlock();
    if (!core.hatchling()
        || source == null
        || ComponentTransport.busy(core.amoebaComponents())
        || core.coreMotion().active()) return;
    var mouth = core.getLevel().getBlockEntity(source);
    if (mouth != null && mouth.getPersistentData().getBoolean("BioshipManualPlacement")) return;
    var state = core.getLevel().getBlockState(source);
    if (!state.is(Create_bioships.FEEDING_MOUTH.get())) return;
    var target = port(core, source);
    // Never point through the body while a destination is blocked or awaiting an affordable move.
    var facing =
        source.equals(target) && FeedingMouthBlock.openFace(core, source, Direction.NORTH)
            ? Direction.NORTH
            : FeedingMouthBlock.outward(core, source);
    if (state.getValue(FeedingMouthBlock.FACING) != facing)
      core.getLevel().setBlock(source, state.setValue(FeedingMouthBlock.FACING, facing), 3);
    var front = core.structuralOrigin().north();
    var blocker = core.getLevel().getBlockEntity(front);
    if (!source.equals(front)
        && blocker != null
        && !(blocker instanceof LivingCoreBlockEntity)
        && !blocker.getPersistentData().getBoolean("BioshipManualPlacement")) {
      for (var offset :
          List.of(new BlockPos(0, 0, 1), new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0))) {
        var bay = core.structuralOrigin().offset(offset);
        var host = core.getLevel().getBlockState(bay);
        if (!bay.equals(source)
            && (host.isAir() || BioshipTissue.isTissue(host))
            && ComponentTransport.begin(core.amoebaComponents(), front, bay, false)) return;
      }
    }
    if (target != null && !target.equals(source))
      ComponentTransport.begin(core.amoebaComponents(), source, target, false);
  }

  static double turn(LivingCoreBlockEntity core, Vec3 goal) {
    if (!core.hatchling() || goal == null) return 0;
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var delta = goal.subtract(root).multiply(1, 0, 1);
    if (delta.lengthSqr() < .04) return 0;
    var forward =
        core.ship().logicalPose().transformNormal(new Vec3(0, 0, -1)).multiply(1, 0, 1).normalize();
    var direction = delta.normalize();
    double angle = Math.atan2(forward.cross(direction).y, forward.dot(direction));
    if (!GroundRoute.turnClear(core, Math.clamp(angle, -.15, .15))) return 0;
    return Math.clamp(angle * 12, -16, 16);
  }
}
