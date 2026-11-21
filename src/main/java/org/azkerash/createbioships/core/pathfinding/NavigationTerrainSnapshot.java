package org.azkerash.createbioships.core.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One synchronous search sees fixed terrain. Never reuse these caches across ticks or searches. */
final class NavigationTerrainSnapshot {
  private final ServerLevel level;
  private final SubLevelObstacles.Snapshot constructs;

  private record Query(AABB bounds, Vec3 offset, double groundPlane, double margin, boolean legs) {}

  private final NavigationFootprint footprint;
  private final Map<Query, Boolean> queries = new HashMap<>();
  private final Map<net.minecraft.world.phys.shapes.VoxelShape, List<AABB>> shapeTemplates =
      new IdentityHashMap<>();
  private final Long2ObjectOpenHashMap<List<AABB>> rows = new Long2ObjectOpenHashMap<>();

  NavigationTerrainSnapshot(ServerLevel level, SubLevelObstacles.Snapshot constructs) {
    this(level, constructs, null);
  }

  NavigationTerrainSnapshot(
      ServerLevel level, SubLevelObstacles.Snapshot constructs, NavigationFootprint footprint) {
    this.level = level;
    this.constructs = constructs;
    this.footprint = footprint;
  }

  boolean clear(AABB query) {
    return clearPose(query, Vec3.ZERO, Double.NEGATIVE_INFINITY, 0, true);
  }

  boolean clearPose(AABB query, Vec3 offset, double groundPlane, double margin, boolean legs) {
    return queries.computeIfAbsent(new Query(query, offset, groundPlane, margin, legs), this::test);
  }

  private boolean intersects(AABB shape, Query query) {
    return shape.intersects(query.bounds)
        && (footprint == null
            || footprint.intersects(
                shape.inflate(query.margin, 0, query.margin),
                query.offset,
                query.groundPlane,
                query.legs));
  }

  private boolean test(Query request) {
    var query = request.bounds;
    var low = BlockPos.containing(query.minX, query.minY, query.minZ);
    var high = BlockPos.containing(query.maxX, query.maxY, query.maxZ);
    if (!level.hasChunksAt(low, high)) {
      return false;
    }
    for (var shape : constructs.boxes()) {
      if (intersects(shape, request)) {
        return false;
      }
    }
    // Cache a horizontal chunk row once. Large creatures repeatedly query wide,
    // mostly-air volumes; subsequent poses visit collision boxes rather than
    // every empty voxel in their complete leg envelope.
    for (int y = low.getY(); y <= high.getY(); y++) {
      for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) {
        for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++) {
          long key = BlockPos.asLong(x, y, z);
          var shapes = rows.get(key);
          if (shapes == null) {
            shapes = captureRow(x, y, z);
            rows.put(key, shapes);
          }
          for (var shape : shapes) {
            if (intersects(shape, request)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private List<AABB> captureRow(int chunkX, int y, int chunkZ) {
    var result = new ArrayList<AABB>();
    var cell = new BlockPos.MutableBlockPos();
    for (int x = chunkX << 4; x < (chunkX + 1) << 4; x++) {
      for (int z = chunkZ << 4; z < (chunkZ + 1) << 4; z++) {
        cell.set(x, y, z);
        var state = level.getBlockState(cell);
        if (state.isAir()) {
          continue;
        }
        var collision = state.getCollisionShape(level, cell);
        if (collision.isEmpty()) {
          continue;
        }
        // Most terrain blocks share a small set of immutable shape instances.
        // Extract each template once, even across thousands of translated cells.
        var template = shapeTemplates.computeIfAbsent(collision, shape -> shape.toAabbs());
        for (var shape : template) {
          result.add(shape.move(x, y, z));
        }
      }
    }
    return List.copyOf(result);
  }
}
