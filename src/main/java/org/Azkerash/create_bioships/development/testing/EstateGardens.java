package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.Heightmap;

/** Useful, overgrown gardens: food, ritual crops, fungi and berries among supported path lights. */
public final class EstateGardens {
  private EstateGardens() {}

  private static boolean nearPath(int x, int z, List<EstatePaths.Step> route, int radius) {
    return route.stream()
        .anyMatch(s -> Math.abs(s.x() - x) <= radius && Math.abs(s.z() - z) <= radius);
  }

  private static boolean ground(Block b) {
    return b == Blocks.GRASS_BLOCK
        || b == Blocks.PODZOL
        || b == Blocks.MOSS_BLOCK
        || b == Blocks.DIRT
        || b == Blocks.COARSE_DIRT;
  }

  private static void cropBed(
      ServerLevel l, BlockPos base, int cx, int cz, CropBlock crop, List<EstatePaths.Step> route) {
    int waterHeight = EstateLandscape.height(cx, cz) - 1;
    l.setBlock(
        base.offset(cx, waterHeight, cz),
        Blocks.STONE_BRICK_SLAB
            .defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.TOP)
            .setValue(SlabBlock.WATERLOGGED, true),
        3);
    for (int dx = -4; dx <= 4; dx++) {
      for (int dz = -3; dz <= 3; dz++) {
        int x = cx + dx, z = cz + dz;
        if (dx * dx / 20d + dz * dz / 12d > 1 || dx == 0 && dz == 0 || nearPath(x, z, route, 2)) {
          continue;
        }
        int actual = EstateLandscape.height(x, z), h = waterHeight + 1;
        var p = base.offset(x, h, z);
        for (int y = h; y <= actual + 1; y++) {
          EstateLandscape.put(l, base.offset(x, y, z), Blocks.AIR);
        }
        for (int y = Math.min(actual - 1, h - 2); y < h; y++) {
          EstateLandscape.put(l, base.offset(x, y, z), Blocks.DIRT);
        }
        if (!l.getBlockState(p).isAir() || !ground(l.getBlockState(p.below()).getBlock())) {
          continue;
        }
        l.setBlock(
            p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        l.setBlock(p, crop.getStateForAge(crop.getMaxAge()), 3);
      }
    }
    var light = base.offset(cx + 5, EstateLandscape.height(cx + 5, cz), cz);
    if (l.getBlockState(light).isAir() && !nearPath(cx + 5, cz, route, 2)) {
      EstateLandscape.put(l, light, Blocks.CHISELED_STONE_BRICKS);
      EstateLandscape.put(l, light.above(), Blocks.LANTERN);
    }
  }

  private static void herbTerrace(ServerLevel l, BlockPos base, List<EstatePaths.Step> route) {
    cropBed(l, base, -102, 62, (CropBlock) Blocks.CARROTS, route);
    cropBed(l, base, -90, 72, (CropBlock) Blocks.POTATOES, route);
    cropBed(l, base, -88, 57, (CropBlock) Blocks.BEETROOTS, route);
    for (int x = -109; x <= -100; x++) {
      for (int z = 72; z <= 79; z++) {
        if (Math.pow((x + 104.5) / 5, 2) + Math.pow((z - 75.5) / 4, 2) > 1
            || nearPath(x, z, route, 2)) {
          continue;
        }
        int h = EstateLandscape.height(x, z);
        var p = base.offset(x, h, z);
        if (!l.getBlockState(p).isAir()) {
          continue;
        }
        EstateLandscape.put(l, p.below(), Blocks.SOUL_SAND);
        l.setBlock(p, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3), 3);
      }
    }
    // A small cistern and sugar-cane bed have actual adjacent water, not decorative crop placement.
    int cx = -113, cz = 65, h = EstateLandscape.height(cx, cz);
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        double r = Math.hypot(x, z);
        if (r > 4.6) {
          continue;
        }
        var p = base.offset(cx + x, h - 1, cz + z);
        int actual = EstateLandscape.height(cx + x, cz + z);
        for (int y = h; y <= actual + 1; y++) {
          EstateLandscape.put(l, base.offset(cx + x, y, cz + z), Blocks.AIR);
        }
        for (int y = Math.min(h - 3, actual - 2); y < h - 1; y++) {
          EstateLandscape.put(l, base.offset(cx + x, y, cz + z), Blocks.CLAY);
        }
        EstateLandscape.put(l, p.below(), Blocks.CLAY);
        EstateLandscape.put(l, p, r < 3.2 ? Blocks.WATER : Blocks.MOSSY_STONE_BRICKS);
        if (r >= 3.2 && r < 4.2 && (x + z) % 2 == 0) {
          EstateLandscape.put(l, p, Blocks.SAND);
          if (Blocks.SUGAR_CANE.defaultBlockState().canSurvive(l, p.above())) {
            EstateLandscape.put(l, p.above(), Blocks.SUGAR_CANE);
          }
        }
      }
    }
    // Low mushroom shelter: a living-looking cap supported by a real stem.
    var stem = base.offset(-111, EstateLandscape.height(-111, 51), 51);
    for (int y = 0; y < 6; y++) {
      EstateLandscape.put(l, stem.above(y), Blocks.MUSHROOM_STEM);
    }
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        if (x * x + z * z < 21) {
          EstateLandscape.put(l, stem.offset(x, 5, z), Blocks.BROWN_MUSHROOM_BLOCK);
        }
      }
    }
    for (int x = -5; x <= 5; x++) {
      for (int z = -5; z <= 5; z++) {
        int gx = -111 + x, gz = 51 + z;
        if (x * x + z * z > 26 || x == 0 && z == 0 || nearPath(gx, gz, route, 2)) {
          continue;
        }
        var p = base.offset(gx, EstateLandscape.height(gx, gz), gz);
        if (l.getBlockState(p).isAir()) {
          EstateLandscape.put(l, p.below(), Blocks.PODZOL);
          EstateLandscape.put(l, p, (x + z) % 3 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
        }
      }
    }
  }

  private static void pathLights(ServerLevel l, BlockPos base, List<EstatePaths.Step> route) {
    var random = new Random(811234);
    for (int i = 8; i < route.size() - 1; i += 18 + random.nextInt(7)) {
      var s = route.get(i);
      var next = route.get(i + 1);
      if (EstateLandscape.central(s.x(), s.z())
          || EstateWaterworks.distance(s.x(), s.z()) < 26
          || Math.abs(s.x() - 65) < 18 && Math.abs(s.z() - 95) < 24
          || Math.abs(s.x() + 91) < 22 && Math.abs(s.z() + 91) < 22) {
        continue;
      }
      Direction side =
          Math.abs(next.x() - s.x()) >= Math.abs(next.z() - s.z())
              ? Direction.SOUTH
              : Direction.EAST;
      int x = s.x() + side.getStepX() * 3, z = s.z() + side.getStepZ() * 3;
      var p = base.offset(x, EstateLandscape.height(x, z), z);
      if (!l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) {
        continue;
      }
      boolean clear = true;
      for (int y = 0; y <= 4; y++) {
        clear &= l.getBlockState(p.above(y)).isAir();
      }
      if (!clear) {
        continue;
      }
      var parts = new ArrayList<BlockPos>();
      for (int y = 0; y <= 4; y++) {
        var q = p.above(y);
        l.setBlock(q, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 3);
        parts.add(q);
      }
      for (int arm = 1; arm <= 2; arm++) {
        var q = p.above(4).relative(side.getOpposite(), arm);
        l.setBlock(q, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 3);
        parts.add(q);
      }
      for (var q : parts) {
        l.setBlock(q, Block.updateFromNeighbourShapes(l.getBlockState(q), l, q), 2);
      }
      l.setBlock(
          p.above(3).relative(side.getOpposite(), 2),
          Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
          3);
    }
  }

  private static void vines(ServerLevel l, BlockPos surface) {
    // Vines only attach where the ruins actually have a supporting face.
    for (int x = 52; x <= 78; x++) {
      for (int z = 77; z <= 113; z++) {
        for (int y = 13; y <= 31; y++) {
          var p = surface.offset(x, y, z);
          if (!l.getBlockState(p).isAir() || Math.floorMod(x * 17 + z * 11 + y * 3, 9) > 2) {
            continue;
          }
          var s = Blocks.VINE.defaultBlockState();
          boolean attached = false;
          for (var d : Direction.Plane.HORIZONTAL) {
            var q = p.relative(d);
            if (l.getBlockState(q).isFaceSturdy(l, q, d.getOpposite())) {
              s = s.setValue(VineBlock.getPropertyForFace(d), true);
              attached = true;
            }
          }
          if (attached) {
            l.setBlock(p, s, 2);
          }
        }
      }
    }
  }

  public static void build(ServerLevel l, BlockPos base) {
    var route = EstatePaths.route();
    var random = new Random(811914);
    // Thin the old evenly scattered flower carpet. Trees, architecture and existing ruins remain.
    for (int x = -42; x <= 42; x++) {
      for (int z = -42; z <= 42; z++) {
        if (Math.max(Math.abs(x), Math.abs(z)) < 20 || Math.abs(x) < 11 && z < -13) {
          continue;
        }
        for (int y = 0; y < 4; y++) {
          var p = base.offset(x, y, z);
          if (l.getBlockState(p).getBlock() instanceof BushBlock
              && Math.floorMod(x * 43 + z * 71, 11) > 2) {
            EstateLandscape.put(l, p, Blocks.AIR);
          }
        }
      }
    }
    Block[] herbs = {
      Blocks.FERN, Blocks.SHORT_GRASS, Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY, Blocks.BLUE_ORCHID
    };
    for (int x = -170; x <= 170; x++) {
      for (int z = -170; z <= 170; z++) {
        if (EstateLandscape.central(x, z)
            || !EstateLandscape.within(x, z)
            || EstateLandscape.protectedRoad(x, z)
            || EstateWaterworks.distance(x, z) < 23
            || nearPath(x, z, route, 2)) {
          continue;
        }
        if (Math.abs(x - 65) < 15 && Math.abs(z - 95) < 21
            || Math.abs(x + 91) < 12 && Math.abs(z + 91) < 12
            || Math.abs(x + 95) < 24 && Math.abs(z - 65) < 24) {
          continue;
        }
        int y =
            l.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX() + x, base.getZ() + z);
        var p = new BlockPos(base.getX() + x, y, base.getZ() + z);
        if (!l.getBlockState(p).isAir() || !ground(l.getBlockState(p.below()).getBlock())) {
          continue;
        }
        double patch = .5 + .5 * Math.sin(x * .11 + Math.cos(z * .09) * 2) * Math.cos(z * .13);
        if (random.nextDouble() > patch * .35) {
          continue;
        }
        if (random.nextInt(12) == 0) {
          l.setBlock(
              p,
              Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3),
              3);
        } else {
          var plant = herbs[random.nextInt(herbs.length)];
          if (plant.defaultBlockState().canSurvive(l, p)) {
            EstateLandscape.put(l, p, plant);
          }
        }
      }
    }
    herbTerrace(l, base, route);
    pathLights(l, base, route);
    vines(l, base);
    var state = CoreTestState.get(l);
    state.denLandscapeVersion = 4;
    state.setDirty();
  }
}
