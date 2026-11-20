package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;

/** A sealed ravine in the otherwise hollow developer floor, with an upper spring and waterfall. */
public final class EstateWaterworks {
  private EstateWaterworks() {}

  public static int riverX(int z) {
    return (int) Math.round(93 + 15 * Math.sin(z * .027));
  }

  static double distance(int x, int z) {
    int along = Math.max(-100, Math.min(100, z));
    return Math.hypot(x - riverX(along), z - along);
  }

  public static void build(ServerLevel l, BlockPos base) {
    for (int z = -119; z <= 119; z++) {
      for (int x = 65; x <= 127; x++) {
        double distance = distance(x, z);
        if (distance >= 19) {
          continue;
        }
        int original = EstateLandscape.height(x, z);
        int bed = (int) Math.min(original - 1, -8 + Math.max(0, distance - 6) * 2.5);
        // The development preset has void below its two floor layers. Seal both the bottom and
        // banks.
        EstateLandscape.put(l, base.offset(x, -12, z), Blocks.BEDROCK);
        for (int y = -11; y <= Math.max(original, bed); y++) {
          Block block =
              y <= bed ? (y == bed && distance < 7 ? Blocks.GRAVEL : Blocks.STONE) : Blocks.AIR;
          if (distance < 7 && y > bed && y <= -4) {
            block = Blocks.WATER;
          }
          EstateLandscape.put(l, base.offset(x, y, z), block);
        }
        if (distance >= 16) {
          EstateLandscape.put(l, base.offset(x, bed, z), Blocks.MOSS_BLOCK);
        }
      }
    }
    int springX = riverX(-100);
    int water = EstateLandscape.height(springX, -112) - 1;
    for (int x = springX - 8; x <= springX + 8; x++) {
      for (int z = -120; z <= -104; z++) {
        double r = Math.hypot(x - springX, z + 112);
        if (r > 8) {
          continue;
        }
        for (int y = -12; y <= water + 2; y++) {
          Block b =
              y <= water - 3
                  ? Blocks.STONE
                  : r < 6 && y <= water
                      ? Blocks.WATER
                      : y <= water ? Blocks.MOSSY_COBBLESTONE : Blocks.AIR;
          if (y == -12) {
            b = Blocks.BEDROCK;
          }
          EstateLandscape.put(l, base.offset(x, y, z), b);
        }
      }
    }
    // Water leaves the spring over a rock lip; the fall and its plunge pool stay inside the sealed
    // cut.
    for (int x = springX - 1; x <= springX + 1; x++) {
      for (int z = -107; z <= -99; z++) {
        for (int y = -3; y <= water + 2; y++) {
          EstateLandscape.put(l, base.offset(x, y, z), Blocks.AIR);
        }
        if (z <= -104) {
          EstateLandscape.put(l, base.offset(x, water, z), Blocks.WATER);
        }
      }
    }
    for (int x = springX - 1; x <= springX + 1; x++) {
      l.scheduleTick(base.offset(x, water, -104), Fluids.WATER, 1);
    }
    EstatePaths.build(l, base);
    var state = CoreTestState.get(l);
    state.denLandscapeVersion = Math.max(2, state.denLandscapeVersion);
    state.setDirty();
  }
}
