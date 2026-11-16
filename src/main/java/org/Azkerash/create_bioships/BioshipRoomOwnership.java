package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;

/** Attribute exterior attachments to their nearest connected room before a room translation. */
final class BioshipRoomOwnership {
  static Map<BlockPos, BioshipBlueprint.Room> assign(
      Set<BlockPos> cells, List<BioshipBlueprint.Room> rooms, BlockPos structuralOffset) {
    var result = new LinkedHashMap<BlockPos, BioshipBlueprint.Room>();
    var queue = new ArrayDeque<BlockPos>();
    for (var pos : cells)
      for (var room : rooms)
        if (room.contains(pos.offset(structuralOffset))) {
          result.put(pos, room);
          queue.add(pos);
          break;
        }
    while (!queue.isEmpty()) {
      var pos = queue.removeFirst();
      for (var direction : Direction.values()) {
        var next = pos.relative(direction);
        if (cells.contains(next) && !result.containsKey(next)) {
          result.put(next, result.get(pos));
          queue.addLast(next);
        }
      }
    }
    return Map.copyOf(result);
  }
}
