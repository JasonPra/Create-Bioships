package org.azkerash.createbioships.compat.letitwalk;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.core.pathfinding.SubLevelObstacles;

/** Full-width terrain/foreign-construct clearance for an emergency foot-placement proposal. */
final class LimbPoseClearance {
  private static final double CONTACT_TOLERANCE_BLOCKS = .025;

  private LimbPoseClearance() {}

  static boolean clear(ServerSubLevel owner, List<Vec3> points, int height, boolean arachnid) {
    double floor = points.getLast().y + CONTACT_TOLERANCE_BLOCKS;
    // This recovery is an upright step-up, not a replacement for adhesive wall transfers.
    if (points.subList(0, 3).stream().anyMatch(point -> point.y < floor)) {
      return false;
    }
    var geometry = CompactLegCollisions.snapshotPose(points, height, arachnid);
    var bounds = geometry.bounds();
    var low = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
    var high = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
    var level = owner.getLevel();
    if (!level.hasChunksAt(low, high)) {
      return false;
    }
    for (var shape : SubLevelObstacles.capture(owner, bounds).boxes()) {
      if (intersects(geometry, shape, floor)) {
        return false;
      }
    }
    for (var cell : BlockPos.betweenClosed(low, high)) {
      var state = level.getBlockState(cell);
      if (state.isAir()) {
        continue;
      }
      var collision = state.getCollisionShape(level, cell);
      if (collision.isEmpty()) {
        continue;
      }
      for (var shape : collision.toAabbs()) {
        if (intersects(geometry, shape.move(cell), floor)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean intersects(
      CompactLegCollisions.Snapshot geometry, AABB box, double floor) {
    return box.maxY > floor
        && geometry.overlaps(
            new AABB(box.minX, Math.max(box.minY, floor), box.minZ, box.maxX, box.maxY, box.maxZ),
            CONTACT_TOLERANCE_BLOCKS);
  }
}
