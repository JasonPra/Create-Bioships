package org.azkerash.createbioships.core.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** One synchronous search sees fixed terrain. Never reuse these caches across ticks or searches. */
final class NavigationTerrainSnapshot {
  private final ServerLevel level;
  private final SubLevelObstacles.Snapshot constructs;
  private final Map<AABB, Boolean> queries = new HashMap<>();
  private final Long2ObjectOpenHashMap<List<AABB>> cells = new Long2ObjectOpenHashMap<>();

  NavigationTerrainSnapshot(ServerLevel level, SubLevelObstacles.Snapshot constructs) {
    this.level = level;
    this.constructs = constructs;
  }

  boolean clear(AABB query) {
    return queries.computeIfAbsent(query, this::test);
  }

  private boolean test(AABB query) {
    var low = BlockPos.containing(query.minX, query.minY, query.minZ);
    var high = BlockPos.containing(query.maxX, query.maxY, query.maxZ);
    if (!level.hasChunksAt(low, high) || !constructs.clear(query)) {
      return false;
    }
    for (var cell : BlockPos.betweenClosed(low, high)) {
      var shapes = cells.get(cell.asLong());
      if (shapes == null) {
        var collision = level.getBlockState(cell).getCollisionShape(level, cell);
        shapes =
            collision.isEmpty()
                ? List.of()
                : collision.toAabbs().stream().map(box -> box.move(cell)).toList();
        cells.put(cell.asLong(), shapes);
      }
      for (var shape : shapes) {
        if (shape.intersects(query)) {
          return false;
        }
      }
    }
    return true;
  }
}
