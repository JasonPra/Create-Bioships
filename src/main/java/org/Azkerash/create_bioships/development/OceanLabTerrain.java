package org.Azkerash.create_bioships.development;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Reproducible seabed relief shared by the developer aquarium and its verification checks. */
public final class OceanLabTerrain {
  public static int floor(int x, int z) {
    int relief =
        5
            + (int)
                Math.round(2 * Math.sin(x * .19) + 2 * Math.cos(z * .16) + Math.sin((x + z) * .11));
    if (Math.abs(x + z / 2) <= 3) relief = 1; // A traversable deep-water trench.
    return -AquaticLab.DEPTH + Math.clamp(relief, 1, 10);
  }

  static int pattern(int x, int z) {
    return Math.floorMod(x * 73428767 ^ z * 912931, 997);
  }

  static Block sediment(int x, int z) {
    int p = pattern(x, z);
    return p % 7 == 0 ? Blocks.GRAVEL : p % 11 == 0 ? Blocks.STONE : Blocks.SAND;
  }

  static Block rock(int x, int y, int z) {
    int archX = -16, archZ = 12, archTop = -AquaticLab.DEPTH + 13;
    boolean span = Math.abs(x - archX) <= 5 && Math.abs(z - archZ) <= 1;
    if (span && y >= floor(x, z) && y <= archTop && (Math.abs(x - archX) >= 4 || y >= archTop - 1))
      return Blocks.STONE;
    double mound = (x - 17) * (x - 17) / 36.0 + (z + 16) * (z + 16) / 25.0;
    if (mound < 1 && y <= floor(x, z) + Math.ceil((1 - mound) * 7)) {
      if (Math.abs(x - 17) <= 2 && y > floor(x, z) && y <= floor(x, z) + 4) return null;
      return pattern(x, z) % 3 == 0 ? Blocks.ANDESITE : Blocks.STONE;
    }
    boolean reef = Math.abs(x - 17) <= 9 && Math.abs(z - 17) <= 8;
    if (reef
        && y > floor(x, z)
        && y <= floor(x, z) + 1 + pattern(x / 2, z / 2) % 4
        && pattern(x / 2, z / 2) % 3 == 0) {
      return switch (pattern(x, z) % 5) {
        case 0 -> Blocks.TUBE_CORAL_BLOCK;
        case 1 -> Blocks.BRAIN_CORAL_BLOCK;
        case 2 -> Blocks.BUBBLE_CORAL_BLOCK;
        case 3 -> Blocks.FIRE_CORAL_BLOCK;
        default -> Blocks.HORN_CORAL_BLOCK;
      };
    }
    return null;
  }
}
