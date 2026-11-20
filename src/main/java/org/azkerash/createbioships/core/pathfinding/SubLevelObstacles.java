package org.azkerash.createbioships.core.pathfinding;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.core.SubLevelCreatureParts;

/** A short-lived collision snapshot of nearby foreign constructs, in world coordinates. */
public final class SubLevelObstacles {
  public record Snapshot(List<AABB> boxes) {
    public Snapshot {
      boxes = List.copyOf(boxes);
    }

    public boolean clear(AABB query) {
      for (var box : boxes) {
        if (box.intersects(query)) {
          return false;
        }
      }
      return true;
    }
  }

  private static final Snapshot EMPTY = new Snapshot(List.of());
  // Collision shapes such as extended flippers can exceed the occupied block's cube.
  private static final double SHAPE_OVERHANG_BLOCKS = 1;

  private SubLevelObstacles() {}

  public static Snapshot capture(ServerSubLevel creature, AABB query) {
    var level = creature.getLevel();
    var container = SubLevelContainer.getContainer(level);
    if (container == null) {
      return EMPTY;
    }
    var own = new HashSet<UUID>();
    for (var member : SubLevelCreatureParts.members(creature)) {
      own.add(member.getUniqueId());
    }
    var boxes = new ArrayList<AABB>();
    for (var candidate :
        container.queryIntersecting(new BoundingBox3d(query.inflate(SHAPE_OVERHANG_BLOCKS)))) {
      if (!(candidate instanceof ServerSubLevel obstacle)
          || obstacle.isRemoved()
          || own.contains(obstacle.getUniqueId())) {
        continue;
      }
      var pose = obstacle.logicalPose();
      var local = transform(query, pose::transformPositionInverse).inflate(SHAPE_OVERHANG_BLOCKS);
      var bounds = obstacle.getPlot().getBoundingBox();
      int minX = Math.max(bounds.minX(), (int) Math.floor(local.minX));
      int minY = Math.max(bounds.minY(), (int) Math.floor(local.minY));
      int minZ = Math.max(bounds.minZ(), (int) Math.floor(local.minZ));
      int maxX = Math.min(bounds.maxX(), (int) Math.floor(local.maxX));
      int maxY = Math.min(bounds.maxY(), (int) Math.floor(local.maxY));
      int maxZ = Math.min(bounds.maxZ(), (int) Math.floor(local.maxZ));
      if (minX > maxX || minY > maxY || minZ > maxZ) {
        continue;
      }
      for (var cell : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
        for (var shape : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs()) {
          // Transform individual collision boxes, not the enclosing plot: openings stay usable.
          // Rotated boxes use conservative world AABBs so navigation never cuts their corners.
          var world = transform(shape.move(cell), pose::transformPosition);
          if (world.intersects(query)) {
            boxes.add(world);
          }
        }
      }
    }
    return boxes.isEmpty() ? EMPTY : new Snapshot(boxes);
  }

  private static AABB transform(AABB box, UnaryOperator<Vec3> transform) {
    double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
    double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
    for (int corner = 0; corner < 8; corner++) {
      var point =
          transform.apply(
              new Vec3(
                  (corner & 1) == 0 ? box.minX : box.maxX,
                  (corner & 2) == 0 ? box.minY : box.maxY,
                  (corner & 4) == 0 ? box.minZ : box.maxZ));
      minX = Math.min(minX, point.x);
      minY = Math.min(minY, point.y);
      minZ = Math.min(minZ, point.z);
      maxX = Math.max(maxX, point.x);
      maxY = Math.max(maxY, point.y);
      maxZ = Math.max(maxZ, point.z);
    }
    return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
  }
}
