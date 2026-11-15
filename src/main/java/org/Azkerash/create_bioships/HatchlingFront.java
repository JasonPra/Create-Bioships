package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

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
          && !tissue.is(Create_bioships.FLESH.get())
          && !tissue.is(Create_bioships.CARAPACE.get())) continue;
      var north = p.north();
      if (core.getLevel().getBlockState(p).is(Create_bioships.FLESH.get())) candidates.add(p);
      if (core.getLevel().getBlockState(north).isAir()) candidates.add(north);
    }
    if (source != null) candidates.add(source);
    candidates.sort(
        Comparator.<BlockPos>comparingInt(BlockPos::getZ)
            .thenComparingInt(p -> Math.abs(p.getX() - origin.getX())));
    for (var p : candidates) {
      if (p.distManhattan(origin) > 6 || !core.getLevel().getBlockState(p.north()).isAir())
        continue;
      var world = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(p));
      if (!core.getLevel().getBlockState(BlockPos.containing(world)).canBeReplaced()) continue;
      if (source != null && p.equals(source)) return p;
      var state = core.getLevel().getBlockState(p);
      if (!state.isAir() && !state.is(Create_bioships.FLESH.get())) continue;
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

  static void arrangeMouth(LivingCoreBlockEntity core) {
    var source = core.mouthBlock();
    if (!core.hatchling() || source == null || OrganMotion.busy(core) || core.coreMotion().active())
      return;
    var mouth = core.getLevel().getBlockEntity(source);
    if (mouth != null && mouth.getPersistentData().getBoolean("BioshipManualPlacement")) return;
    var state = core.getLevel().getBlockState(source);
    if (!state.is(Create_bioships.FEEDING_MOUTH.get())) return;
    if (state.getValue(FeedingMouthBlock.FACING) != Direction.NORTH)
      core.getLevel()
          .setBlock(source, state.setValue(FeedingMouthBlock.FACING, Direction.NORTH), 3);
    var target = port(core, source);
    if (target != null && !target.equals(source)) OrganMotion.begin(core, source, target, false);
  }

  static double turn(LivingCoreBlockEntity core, Vec3 goal) {
    if (!core.hatchling() || goal == null) return 0;
    var root = BioshipBlueprint.physical(core, BlockPos.ZERO);
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
