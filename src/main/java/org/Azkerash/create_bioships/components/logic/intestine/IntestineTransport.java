package org.Azkerash.create_bioships.components.logic.intestine;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;
import org.Azkerash.create_bioships.core.*;

/**
 * Cached face-adjacent pipe routes. No distance searches, cross-grid delivery, or stomach
 * dependency.
 */
public final class IntestineTransport {
  private record Route(
      long revision, long expires, List<BlockPos> pipes, List<BlockPos> stomachs) {}

  private static final Map<Level, Map<BlockPos, Route>> ROUTES = new WeakHashMap<>();
  private static final Map<Level, Long> REVISIONS = new WeakHashMap<>();
  private static final int MAX_VISITED = 4096;

  private IntestineTransport() {}

  public static void invalidate(Level level) {
    REVISIONS.merge(level, 1L, Long::sum);
  }

  public static int transfer(MouthBlockEntity mouth) {
    if (!(mouth.getLevel() instanceof ServerLevel level) || mouth.isEmpty()) return 0;
    var source = CoreAssemblyRules.containing(level, mouth.getBlockPos());
    if (source != null && SubLevelData.isLiving(source) && SubLevelHealth.status(source).dead())
      return 0;
    for (var pos : route(level, mouth.getBlockPos())) {
      if (!(level.getBlockEntity(pos) instanceof StomachBlockEntity stomach)) continue;
      for (int i = 0; i < mouth.getContainerSize(); i++) {
        var stack = mouth.getItem(i);
        if (stack.isEmpty()) continue;
        var one = stack.copyWithCount(1);
        if (!stomach.enqueue(one, true).isEmpty()) continue;
        if (stomach.enqueue(one, false).isEmpty()) {
          mouth.removeItem(i, 1);
          return 1;
        }
      }
    }
    return 0;
  }

  public static List<BlockPos> route(ServerLevel level, BlockPos mouth) {
    var cache = ROUTES.computeIfAbsent(level, l -> new HashMap<>());
    long now = level.getGameTime(), revision = REVISIONS.getOrDefault(level, 0L);
    if (cache.size() > 4096) cache.clear();
    var old = cache.get(mouth);
    if (old != null
        && old.revision == revision
        && now < old.expires
        && old.pipes.stream()
            .allMatch(
                p ->
                    level.hasChunkAt(p)
                        && level.getBlockState(p).is(ComponentRegistry.INTESTINE.get())))
      return old.stomachs;
    var body = CoreAssemblyRules.containing(level, mouth);
    var queue = new ArrayDeque<BlockPos>();
    var seen = new HashSet<BlockPos>();
    var pipes = new ArrayList<BlockPos>();
    var targets = new LinkedHashSet<BlockPos>();
    queue.add(mouth);
    seen.add(mouth);
    while (!queue.isEmpty() && seen.size() < MAX_VISITED) {
      var current = queue.removeFirst();
      for (var d : Direction.values()) {
        var next = current.relative(d);
        if (!seen.add(next)
            || !level.hasChunkAt(next)
            || CoreAssemblyRules.containing(level, next) != body) continue;
        var state = level.getBlockState(next);
        if (state.is(ComponentRegistry.STOMACH.get())) targets.add(next.immutable());
        else if (state.is(ComponentRegistry.INTESTINE.get())) {
          pipes.add(next.immutable());
          queue.addLast(next.immutable());
        }
      }
    }
    var result = List.copyOf(targets);
    cache.put(mouth.immutable(), new Route(revision, now + 100, List.copyOf(pipes), result));
    return result;
  }
}
