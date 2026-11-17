package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.registry.Create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;

/** Face-connected anatomy, independent of Sable's more permissive physical grouping. */
public final class BodyTopology {
  public static Set<BlockPos> structuralCells(Map<BlockPos, BlockState> cells) {
    var result = new HashSet<BlockPos>();
    cells.forEach(
        (pos, state) -> {
          // Locomotion sockets and surface eyes are attachments, never structural bridges.
          if (!BioshipLegEvolution.leg(state) && !state.is(Create_bioships.SURFACE_EYE.get()))
            result.add(pos);
        });
    return result;
  }

  public static int components(Set<BlockPos> cells) {
    var remaining = new HashSet<>(cells);
    int count = 0;
    var queue = new ArrayDeque<BlockPos>();
    while (!remaining.isEmpty()) {
      count++;
      var first = remaining.iterator().next();
      remaining.remove(first);
      queue.add(first);
      while (!queue.isEmpty()) {
        var pos = queue.removeFirst();
        for (var face : Direction.values()) {
          var next = pos.relative(face);
          if (remaining.remove(next)) queue.addLast(next);
        }
      }
    }
    return count;
  }

  static boolean canRemove(Set<BlockPos> cells, BlockPos source) {
    if (!cells.contains(source)) return true;
    var after = new HashSet<>(cells);
    after.remove(source);
    return components(after) <= components(cells);
  }

  static boolean canMove(
      Set<BlockPos> cells, BlockPos source, BlockPos target, boolean retainSource) {
    var after = new HashSet<>(cells);
    if (!retainSource) after.remove(source);
    if (components(after) > components(cells)) return false; // In-transit anatomy matters too.
    return canCommit(cells, source, target, retainSource);
  }

  static boolean canCommit(
      Set<BlockPos> cells, BlockPos source, BlockPos target, boolean retainSource) {
    var after = new HashSet<>(cells);
    if (!retainSource) after.remove(source);
    after.add(target);
    return components(after) <= components(cells);
  }
}
