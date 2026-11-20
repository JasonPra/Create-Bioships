package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;

/** Deterministic authored terrain around the estate; never edits the vault or its central plot. */
public final class EstateLandscape {
  public static final int RADIUS = 200;

  private EstateLandscape() {}

  static double clamp(double n) {
    return Math.max(0, Math.min(1, n));
  }

  static double smooth(double n) {
    n = clamp(n);
    return n * n * (3 - 2 * n);
  }

  private static double hill(int x, int z, int cx, int cz, double width) {
    return Math.exp(
        -((x - cx) * (double) (x - cx) + (z - cz) * (double) (z - cz)) / (width * width));
  }

  public static boolean protectedRoad(int x, int z) {
    return Math.abs(x + 60) <= 6 && z >= -46 || Math.abs(z + 40) <= 6 && x >= -66 && x <= 0;
  }

  public static boolean central(int x, int z) {
    return Math.max(Math.abs(x), Math.abs(z)) <= 42;
  }

  static double edge(int x, int z) {
    double angle = Math.atan2(z, x);
    return 181 + 10 * Math.sin(angle * 3) + 7 * Math.cos(angle * 7);
  }

  public static boolean within(int x, int z) {
    return Math.hypot(x, z) < edge(x, z);
  }

  /**
   * Feet height above the old developer floor; road approaches ease back down to their old level.
   */
  public static int height(int x, int z) {
    if (central(x, z) || protectedRoad(x, z)) return 0;
    double inner = smooth((Math.max(Math.abs(x), Math.abs(z)) - 42) / 27d);
    double outer = smooth((edge(x, z) - Math.hypot(x, z)) / 38d);
    double hills =
        3
            + 24 * hill(x, z, -87, -86, 44)
            + 19 * hill(x, z, 91, -82, 46)
            + 16 * hill(x, z, 63, 100, 50)
            + 12 * hill(x, z, -109, 55, 45);
    hills += 1.4 * Math.sin(x * .055 + z * .023) + 1.1 * Math.cos(z * .067 - x * .019);
    // Two deliberate terraces support the later herb garden and ruined southern sanctuary.
    double garden = smooth((21 - Math.hypot(x + 95, z - 65)) / 7);
    hills = hills * (1 - garden) + 8 * garden;
    double sanctuary = smooth((19 - Math.hypot(x - 65, z - 95)) / 6);
    hills = hills * (1 - sanctuary) + 12 * sanctuary;
    double road = 1;
    if (z >= -46) road = Math.min(road, smooth((Math.abs(x + 60) - 6) / 17d));
    if (x >= -66 && x <= 0) road = Math.min(road, smooth((Math.abs(z + 40) - 6) / 17d));
    return Math.max(0, (int) Math.round(inner * outer * road * hills));
  }

  static Block soil(int x, int z) {
    int pick = Math.floorMod(x * 7349 + z * 9151, 23);
    return pick < 3
        ? Blocks.MOSS_BLOCK
        : pick < 7 && (x < 0 || z > 45) ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
  }

  static void put(ServerLevel l, BlockPos p, Block b) {
    l.setBlock(p, b.defaultBlockState(), 2);
  }

  public static void terrain(ServerLevel l, BlockPos base) {
    for (int x = -49; x <= 49; x++)
      for (int z = -49; z <= 49; z++) {
        if (Math.max(Math.abs(x), Math.abs(z)) != 49) continue;
        for (int y = 0; y < 3; y++)
          if (l.getBlockState(base.offset(x, y, z)).getBlock() instanceof LeavesBlock)
            put(l, base.offset(x, y, z), Blocks.AIR);
      }
    for (int x = -RADIUS; x <= RADIUS; x++)
      for (int z = -RADIUS; z <= RADIUS; z++) {
        if (central(x, z) || protectedRoad(x, z) || !within(x, z)) continue;
        int h = height(x, z);
        // Retire the old square hedge border and plants, without cutting into the central
        // architecture.
        if (Math.max(Math.abs(x), Math.abs(z)) <= 56)
          for (int y = 0; y <= 8; y++)
            if (!l.getBlockState(base.offset(x, y, z)).isAir())
              put(l, base.offset(x, y, z), Blocks.AIR);
        for (int y = 0; y < h - 1; y++)
          put(l, base.offset(x, y, z), y < h - 4 ? Blocks.STONE : Blocks.DIRT);
        Block top = soil(x, z);
        int slope = Math.max(Math.abs(h - height(x + 1, z)), Math.abs(h - height(x, z + 1)));
        if (slope >= 2) top = Blocks.ANDESITE;
        if (Math.hypot(x, z) > edge(x, z) - 8 && Math.floorMod(x * 13 + z * 19, 5) == 0)
          top = Blocks.COARSE_DIRT;
        put(l, base.offset(x, h - 1, z), top);
      }
    var state = CoreTestState.get(l);
    state.denLandscapeVersion = Math.max(1, state.denLandscapeVersion);
    state.setDirty();
  }
}
