package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

/** Grasping obeys solid collision even when an eye can see through glass. */
final class BioshipReach {
  static Vec3 anchor(LivingCoreBlockEntity core, Vec3 target) {
    var pose = core.ship().logicalPose();
    var local = pose.transformPositionInverse(target);
    BlockPos nearest = core.getBlockPos();
    double best = Double.MAX_VALUE;
    for (var p : BioshipDecay.blocks(core.ship())) {
      double next = Vec3.atCenterOf(p).distanceToSqr(local);
      if (next < best) {
        best = next;
        nearest = p;
      }
    }
    var direction = local.subtract(Vec3.atCenterOf(nearest));
    var face = Direction.getNearest(direction.x, direction.y, direction.z);
    return pose.transformPosition(Vec3.atCenterOf(nearest).relative(face, .55));
  }

  static boolean at(LivingCoreBlockEntity core, Vec3 candidate, Vec3 target, boolean blockTarget) {
    return at(core, candidate, target, blockTarget, 5);
  }

  static boolean at(
      LivingCoreBlockEntity core, Vec3 candidate, Vec3 target, boolean blockTarget, double reach) {
    var shift = candidate.subtract(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO));
    var from = anchor(core, target.subtract(shift)).add(shift);
    if (from.distanceToSqr(target) > reach * reach) return false;
    int steps = Math.max(1, (int) Math.ceil(from.distanceTo(target) * 5));
    var end = BlockPos.containing(target);
    var start =
        BlockPos.containing(
            core.ship().logicalPose().transformPositionInverse(from.subtract(shift)));
    for (int i = 1; i < steps; i++) {
      var point = from.lerp(target, (double) i / steps);
      var world = BlockPos.containing(point);
      var state = core.getLevel().getBlockState(world);
      if (!(blockTarget && world.equals(end))
          && !state.getCollisionShape(core.getLevel(), world).isEmpty()) return false;
      var local =
          BlockPos.containing(
              core.ship().logicalPose().transformPositionInverse(point.subtract(shift)));
      if (local.equals(start)) continue;
      state = core.getLevel().getBlockState(local);
      if (!BioshipVision.exposedTube(state)
          && !state.getCollisionShape(core.getLevel(), local).isEmpty()) return false;
    }
    return true;
  }

  static boolean clear(LivingCoreBlockEntity core, Vec3 target, boolean blockTarget, double reach) {
    return at(core, BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO), target, blockTarget, reach);
  }

  static boolean clear(LivingCoreBlockEntity core, Vec3 target, boolean blockTarget) {
    return at(core, BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO), target, blockTarget);
  }
}
