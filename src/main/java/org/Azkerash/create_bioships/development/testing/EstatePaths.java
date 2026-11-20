package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;

/**
 * Graded walking loop with half-block rises; bridge arches spring from sealed ravine foundations.
 */
public final class EstatePaths {
  public record Step(int x, int z, double height) {
    public Vec3 feet(BlockPos base) {
      return new Vec3(
          base.getX() + x + .5,
          base.getY() + height - (height % 1 == 0 ? .0625 : 0),
          base.getZ() + z + .5);
    }
  }

  private record Cell(int x, int z) {}

  private record Tread(double height, int distance) {}

  private EstatePaths() {}

  public static List<Step> route() {
    int[][] points = {
      {0, -40},
      {-40, -55},
      {-80, -50},
      {-105, 0},
      {-95, 65},
      {-40, 85},
      {30, 100},
      {65, 95},
      {85, 35},
      {125, 35},
      {130, -35},
      {100, -85},
      {55, -95},
      {0, -40}
    };
    var cells = new ArrayList<Cell>();
    for (int k = 1; k < points.length; k++) {
      var a = points[k - 1];
      var b = points[k];
      int n = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
      for (int i = 0; i <= n; i++) {
        var c =
            new Cell(
                (int) Math.round(a[0] + (b[0] - a[0]) * (i / (double) n)),
                (int) Math.round(a[1] + (b[1] - a[1]) * (i / (double) n)));
        if (cells.isEmpty() || !cells.getLast().equals(c)) cells.add(c);
      }
    }
    var heights = new double[cells.size()];
    for (int i = 0; i < cells.size(); i++) {
      var c = cells.get(i);
      heights[i] = EstateLandscape.height(c.x, c.z);
      if (Math.abs(c.z - 35) <= 2 && Math.abs(c.x - EstateWaterworks.riverX(35)) <= 21)
        heights[i] = 12;
      if (Math.abs(c.z + 90) <= 10 && Math.abs(c.x - EstateWaterworks.riverX(-90)) <= 21)
        heights[i] = 20;
    }
    heights[0] = 0;
    heights[heights.length - 1] = 0;
    for (int pass = 0; pass < 3; pass++) {
      for (int i = 1; i < heights.length; i++)
        heights[i] = Math.max(heights[i - 1] - .5, Math.min(heights[i - 1] + .5, heights[i]));
      for (int i = heights.length - 2; i >= 0; i--)
        heights[i] = Math.max(heights[i + 1] - .5, Math.min(heights[i + 1] + .5, heights[i]));
    }
    var result = new ArrayList<Step>();
    for (int i = 0; i < cells.size(); i++)
      result.add(new Step(cells.get(i).x, cells.get(i).z, heights[i]));
    return List.copyOf(result);
  }

  private static void bridge(ServerLevel l, BlockPos base, int z, int height) {
    int center = EstateWaterworks.riverX(z);
    for (int dx = -22; dx <= 22; dx++)
      for (int dz = -3; dz <= 3; dz++) {
        int bottom = height - 3 - (int) Math.round(10 * Math.pow(Math.abs(dx) / 22d, 2));
        if (Math.abs(dx) >= 20) bottom = -11;
        for (int y = bottom; y < height; y++)
          EstateLandscape.put(
              l,
              base.offset(center + dx, y, crossingZ(z, dx) + dz),
              Math.abs(dz) == 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
        if (Math.abs(dz) == 3) {
          l.setBlock(
              base.offset(center + dx, height, crossingZ(z, dx) + dz),
              Blocks.STONE_BRICK_WALL.defaultBlockState(),
              3);
          if (dx % 7 == 0)
            EstateLandscape.put(
                l, base.offset(center + dx, height + 1, crossingZ(z, dx) + dz), Blocks.LANTERN);
        }
      }
  }

  private static int crossingZ(int z, int dx) {
    return z < 0 ? z + (int) Math.round(dx * (10d / 45)) : z;
  }

  private static boolean bridgeSpan(int x, int z) {
    for (int crossing : new int[] {35, -90}) {
      int dx = x - EstateWaterworks.riverX(crossing);
      if (Math.abs(dx) <= 22 && Math.abs(z - crossingZ(crossing, dx)) <= 3) return true;
    }
    return false;
  }

  public static void build(ServerLevel l, BlockPos base) {
    bridge(l, base, 35, 12);
    bridge(l, base, -90, 20);
    var treads = new LinkedHashMap<Cell, Tread>();
    for (var s : route())
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++) {
          var cell = new Cell(s.x + x, s.z + z);
          int d = x * x + z * z;
          var old = treads.get(cell);
          if (old == null || d < old.distance) treads.put(cell, new Tread(s.height, d));
        }
    for (var entry : treads.entrySet()) {
      var c = entry.getKey();
      double h = entry.getValue().height;
      int blockY = (int) Math.ceil(h) - 1;
      if (!bridgeSpan(c.x, c.z))
        for (int y = Math.min(-1, blockY - 2); y < blockY; y++)
          EstateLandscape.put(l, base.offset(c.x, y, c.z), Blocks.STONE);
      var state =
          h % 1 == 0
              ? DevelopmentTestBlocks.PATH.get().defaultBlockState()
              : DevelopmentTestBlocks.PATH_SLAB.get().defaultBlockState();
      l.setBlock(base.offset(c.x, blockY, c.z), state, 2);
      for (int y = blockY + 1; y <= Math.max(blockY + 3, EstateLandscape.height(c.x, c.z) + 2); y++)
        EstateLandscape.put(l, base.offset(c.x, y, c.z), Blocks.AIR);
    }
  }
}
