package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Large reusable flip-test space. Upgrades change fixture walls, never simulated creatures. */
public final class DevelopmentLegChambers {
  public static final int HALF_WIDTH = 18, FRONT = -8, BACK = 40, ROOF = 24;

  private DevelopmentLegChambers() {}

  public static void build(ServerLevel level, BlockPos base, boolean reset, int oldRoof) {
    for (int cx = (base.getX() - HALF_WIDTH) >> 4; cx <= (base.getX() + HALF_WIDTH) >> 4; cx++) {
      for (int cz = (base.getZ() + FRONT) >> 4; cz <= (base.getZ() + BACK) >> 4; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    var glass = DevelopmentTestBlocks.GLASS.get();
    for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
      for (int z = FRONT; z <= BACK; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
        for (int y = 0; y <= ROOF; y++) {
          var pos = base.offset(x, y, z);
          boolean shell = Math.abs(x) == HALF_WIDTH || z == FRONT || z == BACK || y == ROOF;
          if (shell) {
            level.setBlock(pos, glass.defaultBlockState(), 2);
          } else if (reset
              || (Math.abs(x) <= 10
                  && z <= 24
                  && y <= oldRoof
                  && (Math.abs(x) == 10 || z == 24 || y == oldRoof)
                  && level.getBlockState(pos).is(glass))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
          }
        }
      }
    }
  }
}
