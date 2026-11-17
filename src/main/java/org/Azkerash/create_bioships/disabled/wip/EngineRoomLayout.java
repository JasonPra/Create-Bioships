package org.Azkerash.create_bioships.disabled.wip;

import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.BioshipTissue;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Shared shell geometry and layout-coordinate mapping; no mutations or spending. */
public final class EngineRoomLayout {
  static boolean skin(BlockState state) {
    return BioshipTissue.isTissue(state)
        || state.is(Create_bioships.CARAPACE.get())
        || state.is(Create_bioships.ONE_WAY_WINDOW.get());
  }

  public static List<DinghyGrowth.Part> body(int version, int columns) {
    if (columns <= 1) return DinghyGrowth.body(version);
    int delta = EngineRoomSizing.widening(columns);
    var original = DinghyGrowth.body(version);
    var result = new LinkedHashMap<BlockPos, BlockState>();
    for (var part : original) {
      var p = part.offset();
      var state = part.state();
      if (p.getZ() > 0 && p.getX() != 0 && skin(state))
        result.put(p.offset(Integer.signum(p.getX()) * delta, 0, 0), state);
      else result.put(p, state);
      if (p.getZ() >= 0 && p.getX() == 0 && skin(state) && (p.getY() <= 1 || p.getY() >= 5))
        for (int x = -delta; x <= delta; x++) result.put(p.offset(x, 0, 0), state);
    }
    var flesh = BioshipTissue.body().defaultBlockState();
    for (int z : new int[] {0, 6})
      for (int y = 1; y <= 6; y++) {
        int width = (z == 0 ? 3 : 2) + delta - Math.max(0, y - 4);
        for (int x = -width; x <= width; x++) {
          if (z == 0 && Math.abs(x) <= 3 || z == 6 && x == 0 && y <= 3) continue;
          result.putIfAbsent(new BlockPos(x, y, z), flesh);
        }
      }
    return result.entrySet().stream()
        .map(e -> new DinghyGrowth.Part(e.getKey(), e.getValue()))
        .toList();
  }

  static BlockPos at(LivingCoreBlockEntity core, BlockPos local) {
    return core.structuralOrigin().offset(core.blueprint().offset(local));
  }
}
