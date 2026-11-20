package org.Azkerash.create_bioships.core.brain;

import java.util.*;
import net.minecraft.core.*;

/** Shared placement geometry only; no inventory or behavior execution lives on brain blocks. */
public final class BrainShape {
  public static final int MAX_BLOCKS = 27;

  private BrainShape() {}

  public static boolean bounded(Collection<BlockPos> cells) {
    if (cells.isEmpty() || cells.size() > MAX_BLOCKS) {
      return false;
    }
    for (var axis : Direction.Axis.values()) {
      int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
      for (var p : cells) {
        int value = axis.choose(p.getX(), p.getY(), p.getZ());
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
      if (max - min >= 3) {
        return false;
      }
    }
    return true;
  }

  public static boolean canAdd(Set<BlockPos> cells, BlockPos target) {
    if (cells.contains(target) || cells.size() >= MAX_BLOCKS) {
      return false;
    }
    if (!cells.isEmpty()
        && Arrays.stream(Direction.values()).noneMatch(d -> cells.contains(target.relative(d)))) {
      return false;
    }
    var expanded = new HashSet<>(cells);
    expanded.add(target);
    return bounded(expanded) && groups(expanded).size() == 1;
  }

  public static List<Set<BlockPos>> groups(Set<BlockPos> cells) {
    var left = new HashSet<>(cells);
    var groups = new ArrayList<Set<BlockPos>>();
    while (!left.isEmpty()) {
      var group = new HashSet<BlockPos>();
      var queue = new ArrayDeque<BlockPos>();
      var first = left.iterator().next();
      left.remove(first);
      queue.add(first);
      while (!queue.isEmpty()) {
        var p = queue.removeFirst();
        group.add(p);
        for (var d : Direction.values()) {
          var next = p.relative(d);
          if (left.remove(next)) {
            queue.add(next);
          }
        }
      }
      groups.add(Set.copyOf(group));
    }
    groups.sort(
        Comparator.<Set<BlockPos>>comparingInt(Set::size)
            .reversed()
            .thenComparing(g -> g.stream().min(BlockPos::compareTo).orElseThrow()));
    return List.copyOf(groups);
  }
}
