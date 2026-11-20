package org.azkerash.createbioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.*;

/** Remains of a larger sanctuary and watchtower, surrounded by irregular mature woodland. */
public final class EstateRuins {
  private EstateRuins() {}

  private static void fill(
      ServerLevel l, BlockPos p, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
    for (int x = x0; x <= x1; x++) {
      for (int y = y0; y <= y1; y++) {
        for (int z = z0; z <= z1; z++) {
          if (!DevelopmentTestBlocks.protectedBlock(l.getBlockState(p.offset(x, y, z)))) {
            EstateLandscape.put(l, p.offset(x, y, z), b);
          }
        }
      }
    }
  }

  private static Block masonry(int x, int y, int z) {
    return Math.floorMod(x * 17 + y * 31 + z * 23, 7) < 2
        ? Blocks.MOSSY_STONE_BRICKS
        : Blocks.CRACKED_DEEPSLATE_BRICKS;
  }

  private static void sanctuary(ServerLevel l, BlockPos surface) {
    var p = surface.offset(65, 12, 95);
    // Founded platform; the extant arches and low broken walls never float above the terrain.
    fill(l, p, -12, -12, -18, 12, -2, 18, Blocks.STONE);
    for (int x = -11; x <= 11; x++) {
      for (int z = -17; z <= 17; z++) {
        var floor = p.offset(x, -1, z);
        if (!DevelopmentTestBlocks.protectedBlock(l.getBlockState(floor))) {
          EstateLandscape.put(l, floor, Blocks.MOSSY_STONE_BRICKS);
        }
        int oldHeight = EstateLandscape.height(65 + x, 95 + z) - 12;
        for (int y = 0; y <= Math.max(2, oldHeight + 1); y++) {
          var at = p.offset(x, y, z);
          if (!DevelopmentTestBlocks.protectedBlock(l.getBlockState(at))) {
            EstateLandscape.put(l, at, Blocks.AIR);
          }
        }
      }
    }
    for (int x = -10; x <= 10; x++) {
      for (int z = -16; z <= 16; z++) {
        if (Math.abs(x) != 10 && Math.abs(z) != 16) {
          continue;
        }
        if (z == -16 && x >= 3 && x <= 8 || x == -10 && Math.abs(z) <= 3) {
          continue;
        }
        int height = 2 + Math.floorMod(x * 17 + z * 11, 4);
        for (int y = 0; y < height; y++) {
          EstateLandscape.put(l, p.offset(x, y, z), masonry(x, y, z));
        }
      }
    }
    for (int z : new int[] {-12, 6, 14}) {
      for (int x : new int[] {-8, 8}) {
        fill(l, p, x - 1, 0, z - 1, x + 1, 1, z + 1, Blocks.CHISELED_DEEPSLATE);
        fill(l, p, x, 2, z, x, 14, z, Blocks.DEEPSLATE_BRICKS);
        fill(l, p, x - 1, 13, z - 1, x + 1, 14, z + 1, Blocks.CHISELED_DEEPSLATE);
      }
      for (int x = -8; x <= 8; x++) {
        int y = 14 + (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - Math.pow(x / 8d, 2))));
        int neighbor =
            14
                + (int)
                    Math.round(
                        5
                            * Math.sqrt(
                                Math.max(0, 1 - Math.pow(Math.min(8, Math.abs(x) + 1) / 8d, 2))));
        fill(l, p, x, Math.min(y, neighbor), z - 1, x, y + 1, z + 1, Blocks.DEEPSLATE_BRICKS);
      }
    }
    // A raised apse and altar remain recognisable among the fallen masonry.
    fill(l, p, -4, 0, 10, 4, 0, 15, Blocks.POLISHED_BLACKSTONE_BRICKS);
    fill(l, p, -2, 1, 12, 2, 2, 14, Blocks.CHISELED_POLISHED_BLACKSTONE);
    for (int x : new int[] {-4, 4}) {
      EstateLandscape.put(l, p.offset(x, 1, 12), Blocks.BONE_BLOCK);
      EstateLandscape.put(l, p.offset(x, 2, 12), Blocks.SOUL_LANTERN);
    }
    var random = new Random(81173);
    for (int i = 0; i < 45; i++) {
      int x = random.nextInt(19) - 9, z = random.nextInt(31) - 15;
      if (Math.abs(x) <= 2 || z >= 10 || !clearOfPaths(65 + x, 95 + z, EstatePaths.route())) {
        continue;
      }
      EstateLandscape.put(
          l, p.offset(x, 0, z), i % 3 == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.COBBLESTONE);
      if (i % 5 == 0) {
        EstateLandscape.put(l, p.offset(x, 1, z), Blocks.MOSSY_COBBLESTONE_WALL);
      }
    }
  }

  private static void watchtower(ServerLevel l, BlockPos surface) {
    int cx = -91, cz = -91, ground = EstateLandscape.height(cx, cz);
    var p = surface.offset(cx, ground, cz);
    for (int x = -9; x <= 9; x++) {
      for (int z = -9; z <= 9; z++) {
        double r = Math.hypot(x, z);
        if (r > 9) {
          continue;
        }
        int terrain = EstateLandscape.height(cx + x, cz + z) - ground;
        for (int y = Math.min(terrain - 1, -2); y < 0; y++) {
          EstateLandscape.put(l, p.offset(x, y, z), Blocks.STONE);
        }
        EstateLandscape.put(l, p.offset(x, -1, z), Blocks.MOSSY_STONE_BRICKS);
        if (r < 6.5) {
          for (int y = 0; y <= Math.max(terrain, 0) + 2; y++) {
            EstateLandscape.put(l, p.offset(x, y, z), Blocks.AIR);
          }
          continue;
        }
        if (x > 3 && z < 0) {
          continue;
        }
        int wall = 3 + (int) Math.round(13 * (.5 + .5 * Math.sin(Math.atan2(z, x) + .7)));
        if (z < 0 && Math.abs(x) <= 2) {
          wall = 0;
        }
        for (int y = 0; y < wall; y++) {
          EstateLandscape.put(l, p.offset(x, y, z), masonry(x, y, z));
        }
      }
    }
    // A collapsed buttress rests on the ridge rather than hanging in mid-air.
    for (int i = 0; i < 14; i++) {
      int x = cx + 8 + i, z = cz + 3 + i / 3, h = EstateLandscape.height(x, z);
      fill(
          l, surface, x, h, z, x + 1, h + Math.max(0, 3 - i / 4), z + 1, Blocks.CHISELED_DEEPSLATE);
    }
  }

  private static boolean clearOfPaths(int x, int z, List<EstatePaths.Step> route) {
    return route.stream().noneMatch(s -> Math.abs(s.x() - x) <= 5 && Math.abs(s.z() - z) <= 5);
  }

  private static void leaves(ServerLevel l, BlockPos p, Block foliage, int rx, int ry) {
    for (int x = -rx; x <= rx; x++) {
      for (int y = -ry; y <= ry; y++) {
        for (int z = -rx; z <= rx; z++) {
          if (x * x / (double) (rx * rx) + z * z / (double) (rx * rx) + y * y / (double) (ry * ry)
              > 1.2) {
            continue;
          }
          var q = p.offset(x, y, z);
          if (l.getBlockState(q).isAir()) {
            l.setBlock(
                q,
                foliage.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Block.UPDATE_CLIENTS);
          }
        }
      }
    }
  }

  private static void tree(ServerLevel l, BlockPos p, Random random, int kind) {
    int height = kind == 1 ? 13 + random.nextInt(7) : 9 + random.nextInt(7);
    Block wood = kind == 1 ? Blocks.SPRUCE_LOG : kind == 2 ? Blocks.BIRCH_LOG : Blocks.DARK_OAK_LOG;
    Block foliage =
        kind == 1 ? Blocks.SPRUCE_LEAVES : kind == 2 ? Blocks.BIRCH_LEAVES : Blocks.DARK_OAK_LEAVES;
    for (int y = 0; y <= height; y++) {
      EstateLandscape.put(l, p.above(y), wood);
    }
    if (kind == 1) {
      for (int y = 3; y < height; y += 2) {
        leaves(l, p.above(y), foliage, Math.max(1, (height - y) / 4), 1);
      }
      leaves(l, p.above(height), foliage, 1, 2);
      return;
    }
    for (int branch = 0; branch < 3; branch++) {
      double angle = random.nextDouble() * Math.PI * 2;
      int length = 3 + random.nextInt(3);
      int start = height - 5 + branch;
      for (int i = 1; i <= length; i++) {
        int x = (int) Math.round(Math.cos(angle) * i), z = (int) Math.round(Math.sin(angle) * i);
        EstateLandscape.put(l, p.offset(x, start + i / 2, z), wood);
      }
      if (kind != 3) {
        leaves(
            l,
            p.offset(
                (int) Math.round(Math.cos(angle) * length),
                start + length / 2,
                (int) Math.round(Math.sin(angle) * length)),
            foliage,
            3,
            2);
      }
    }
    if (kind != 3) {
      leaves(l, p.above(height - 1), foliage, 4, 3);
    }
  }

  public static void build(ServerLevel l, BlockPos base) {
    sanctuary(l, base);
    watchtower(l, base);
    var random = new Random(839217);
    var chosen = new ArrayList<BlockPos>();
    var route = EstatePaths.route();
    for (int attempt = 0; attempt < 1500 && chosen.size() < 155; attempt++) {
      int x = random.nextInt(331) - 165, z = random.nextInt(331) - 165;
      if (!EstateLandscape.within(x, z)
          || Math.hypot(x, z) > 166
          || Math.max(Math.abs(x), Math.abs(z)) < 50
          || EstateLandscape.protectedRoad(x, z)
          || EstateWaterworks.distance(x, z) < 27
          || !clearOfPaths(x, z, route)) {
        continue;
      }
      if (Math.abs(x - 65) < 18 && Math.abs(z - 95) < 23
          || Math.abs(x + 91) < 20 && Math.abs(z + 91) < 20
          || Math.abs(x + 95) < 25 && Math.abs(z - 65) < 25) {
        continue;
      }
      var p = base.offset(x, EstateLandscape.height(x, z), z);
      if (chosen.stream()
          .anyMatch(
              old -> old.distToCenterSqr(p.getX() + .5, old.getY() + .5, p.getZ() + .5) < 100)) {
        continue;
      }
      int pick = random.nextInt(12);
      tree(l, p, random, pick < 4 ? 1 : pick == 4 ? 2 : pick == 5 ? 3 : 0);
      chosen.add(p);
    }
    for (int i = 0; i < 28; i++) {
      int x = random.nextInt(281) - 140, z = random.nextInt(281) - 140;
      if (Math.max(Math.abs(x), Math.abs(z)) < 48
          || EstateLandscape.protectedRoad(x, z)
          || !clearOfPaths(x, z, route)
          || EstateWaterworks.distance(x, z) < 23
          || Math.abs(x + 95) < 25 && Math.abs(z - 65) < 25) {
        continue;
      }
      int radius = 2 + random.nextInt(4), h = EstateLandscape.height(x, z);
      final int bx = x, bz = z, clearance = radius + 4;
      if (route.stream()
              .anyMatch(
                  step ->
                      Math.abs(step.x() - bx) <= clearance && Math.abs(step.z() - bz) <= clearance)
          || Math.abs(x - 65) < 24 && Math.abs(z - 95) < 28
          || Math.abs(x + 91) < 23 && Math.abs(z + 91) < 23) {
        continue;
      }
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          for (int y = Math.min(-2, EstateLandscape.height(x + dx, z + dz) - h - 1);
              y <= radius;
              y++) {
            if (dx * dx + dz * dz < radius * radius
                && (y <= -2 || dx * dx + dz * dz + y * y * 1.7 < radius * radius)) {
              EstateLandscape.put(
                  l,
                  base.offset(x + dx, h + y, z + dz),
                  Math.floorMod(dx + dz + y, 5) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.ANDESITE);
            }
          }
        }
      }
    }
    var state = CoreTestState.get(l);
    state.setDenLandscapeVersion(Math.max(3, state.getDenLandscapeVersion()));
    state.setDirty();
  }
}
