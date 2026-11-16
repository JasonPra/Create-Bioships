package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only Sable geometry adapter shared by sensing, support, repair and decay. */
public final class BioshipStructure {
  public static void visit(SubLevel ship, BiConsumer<BlockPos, BlockState> visitor) {
    for (var holder : ship.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int index = 0; index < sections.length; index++) {
        var section = sections[index];
        if (section.hasOnlyAir()) continue;
        int sy = chunk.getSectionYFromSectionIndex(index) * 16;
        for (int y = 0; y < 16; y++)
          for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
              var state = section.getBlockState(x, y, z);
              if (!state.isAir())
                visitor.accept(
                    new BlockPos(
                        chunk.getPos().getMinBlockX() + x,
                        sy + y,
                        chunk.getPos().getMinBlockZ() + z),
                    state);
            }
      }
    }
  }

  static Map<BlockPos, BlockState> cells(SubLevel ship) {
    var result = new LinkedHashMap<BlockPos, BlockState>();
    visit(ship, result::put);
    return result;
  }

  static List<BlockPos> positions(SubLevel ship) {
    var result = new ArrayList<BlockPos>();
    visit(ship, (pos, state) -> result.add(pos));
    return result;
  }

  static int count(SubLevel ship) {
    var count = new int[] {0};
    visit(ship, (pos, state) -> count[0]++);
    return count[0];
  }
}
