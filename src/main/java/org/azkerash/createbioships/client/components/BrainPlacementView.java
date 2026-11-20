package org.azkerash.createbioships.client.components;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.brain.BrainShape;

/** Local placement prediction, derived only when brain topology changes; never authoritative. */
public final class BrainPlacementView {
  private static final Map<SubLevel, Set<BlockPos>> CACHE = new WeakHashMap<>();

  private BrainPlacementView() {}

  private static SubLevel body(Level level, BlockPos pos) {
    var c = SubLevelContainer.getContainer(level);
    if (c == null || !c.inBounds(pos)) {
      return null;
    }
    var plot = c.getPlot(new ChunkPos(pos));
    return plot == null ? null : plot.getSubLevel();
  }

  public static void invalidate(Level level, BlockPos pos) {
    var body = body(level, pos);
    if (body != null) {
      CACHE.remove(body);
    }
  }

  public static boolean canPlace(Level level, BlockPos pos) {
    var body = body(level, pos);
    if (body == null) {
      return true;
    }
    return BrainShape.canAdd(CACHE.computeIfAbsent(body, BrainPlacementView::scan), pos);
  }

  private static Set<BlockPos> scan(SubLevel body) {
    var cells = new HashSet<BlockPos>();
    for (var holder : body.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
        var section = sections[sectionIndex];
        if (!section.maybeHas(s -> s.is(ComponentRegistry.BRAIN.get()))) {
          continue;
        }
        int floor = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
        for (int x = 0; x < 16; x++) {
          for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
              if (section.getBlockState(x, y, z).is(ComponentRegistry.BRAIN.get())) {
                cells.add(
                    new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        floor + y,
                        chunk.getPos().getMinBlockZ() + z));
              }
            }
          }
        }
      }
    }
    return Set.copyOf(cells);
  }
}
