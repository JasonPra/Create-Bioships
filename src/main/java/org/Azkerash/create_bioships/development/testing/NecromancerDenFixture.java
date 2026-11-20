package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** An optional authored development-world landmark. It never assembles or duplicates a creature. */
public final class NecromancerDenFixture {
  public static final String MARK = "bioships.necromancer_den";
  private static final int HALF = 43, HEIGHT = 36;

  private NecromancerDenFixture() {}

  public static BlockPos base(ServerLevel l) {
    return new BlockPos(
        -1024,
        Math.max(l.getMinBuildHeight() + 8, CoreTestState.get(l).layout().base().getY() - 56),
        -1024);
  }

  public static BlockPos surface(ServerLevel l) {
    return new BlockPos(-1024, CoreTestState.get(l).layout().base().getY(), -1024);
  }

  private static void put(ServerLevel l, BlockPos b, int x, int y, int z, Block block) {
    l.setBlock(b.offset(x, y, z), block.defaultBlockState(), 2);
  }

  private static void fill(
      ServerLevel l, BlockPos b, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
    for (int x = x0; x <= x1; x++)
      for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) put(l, b, x, y, z, block);
  }

  private static void force(ServerLevel l, BlockPos b, int radius) {
    for (int x = (b.getX() - radius) >> 4; x <= (b.getX() + radius) >> 4; x++)
      for (int z = (b.getZ() - radius) >> 4; z <= (b.getZ() + radius) >> 4; z++)
        l.setChunkForced(x, z, true);
  }

  public static void build(ServerLevel l) {
    var b = base(l);
    var s = surface(l);
    force(l, s, 65);
    // Carve the buried vault. Bedrock remains outside its deliberately bounded shell.
    for (int x = -HALF; x <= HALF; x++)
      for (int z = -HALF; z <= HALF; z++)
        for (int y = -2; y <= HEIGHT; y++) {
          boolean wall = Math.abs(x) == HALF || Math.abs(z) == HALF;
          Block block = Blocks.AIR;
          if (y == -2) block = Blocks.BEDROCK;
          else if (y == -1)
            block =
                Math.floorMod(x + z, 7) == 0
                    ? Blocks.CRACKED_DEEPSLATE_TILES
                    : Blocks.DEEPSLATE_TILES;
          else if (wall || y == HEIGHT)
            block =
                Math.floorMod(x * 7 + z * 11 + y * 3, 17) < 3
                    ? Blocks.CRACKED_DEEPSLATE_BRICKS
                    : Blocks.DEEPSLATE_BRICKS;
          put(l, b, x, y, z, block);
        }
    // Ritual inlay around the central glass garden, with clear approach paths.
    for (int x = -23; x <= 23; x++)
      for (int z = -23; z <= 23; z++) {
        int ring = Math.max(Math.abs(x), Math.abs(z));
        if (ring == 20 || ring == 23) put(l, b, x, -1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        else if (ring == 21 && Math.floorMod(x + z, 4) == 0)
          put(l, b, x, -1, z, Blocks.RED_NETHER_BRICKS);
      }
    int[][] columns = {
      {-27, -27},
      {27, -27},
      {-27, 27},
      {27, 27},
      {-27, 0},
      {-14, 27},
      {14, 27},
      {14, -27},
      {-14, -27}
    };
    for (var c : columns) column(l, b, c[0], c[1]);
    // Broken rib-vaults span high above the enclosure rather than crossing its walking volume.
    for (int z : new int[] {-27, 27})
      for (int x = -27; x <= 27; x++) {
        int y = 29 + (int) Math.round(5 * (1 - Math.abs(x) / 27d));
        fill(l, b, x, y, z - 1, x, y + 1, z + 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
      }
    for (int x : new int[] {-27, 27})
      for (int z = -27; z <= 27; z++) {
        int y = 29 + (int) Math.round(5 * (1 - Math.abs(z) / 27d));
        fill(l, b, x - 1, y, z, x + 1, y + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
      }
    DevelopmentHabitatFixtures.buildJuniorPen(l, b);
    laboratory(l, b);
    unfinishedCreation(l, b);
    for (int z : new int[] {-27, -14, -1}) holdingCell(l, b, z);
    for (int x : new int[] {-38, 38})
      for (int z : new int[] {-35, 0, 35}) {
        put(l, b, x, 1, z, Blocks.BONE_BLOCK);
        put(l, b, x, 2, z, Blocks.SKELETON_SKULL);
        put(l, b, x + 1, 0, z, Blocks.SOUL_LANTERN);
      }
    gardens(l, s);
    tower(l, s);
    roads(l, s);
    detailExterior(l, s);
    repairAccessAndLighting(l);
    var state = CoreTestState.get(l);
    state.necromancerDenBuilt = true;
    state.setDirty();
    l.setDefaultSpawnPos(s.offset(0, 0, -20), 0);
  }

  private static void column(ServerLevel l, BlockPos b, int x, int z) {
    fill(l, b, x - 2, 0, z - 2, x + 2, 1, z + 2, Blocks.CHISELED_POLISHED_BLACKSTONE);
    fill(l, b, x - 1, 2, z - 1, x + 1, 27, z + 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
    fill(l, b, x - 2, 27, z - 2, x + 2, 29, z + 2, Blocks.CHISELED_DEEPSLATE);
    for (int y : new int[] {7, 17, 26})
      for (var face : Direction.Plane.HORIZONTAL)
        put(l, b, x + face.getStepX() * 2, y, z + face.getStepZ() * 2, Blocks.BONE_BLOCK);
    var hook =
        Blocks.DEEPSLATE_BRICK_WALL
            .defaultBlockState()
            .setValue(WallBlock.NORTH_WALL, WallSide.LOW)
            .setValue(WallBlock.SOUTH_WALL, WallSide.LOW);
    l.setBlock(b.offset(x, 4, z - 2), hook, 2);
    l.setBlock(b.offset(x, 4, z - 3), hook, 2);
    l.setBlock(
        b.offset(x, 3, z - 3),
        Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
        2);
  }

  private static void biological(ServerLevel l, BlockPos p, String name) {
    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:" + name));
    l.setBlock(p, block.defaultBlockState(), 2);
  }

  private static void laboratory(ServerLevel l, BlockPos b) {
    fill(l, b, 20, 0, -39, 39, 0, -34, Blocks.POLISHED_BLACKSTONE_BRICKS);
    var machines =
        new String[] {
          "bio_forge", "bio_lab", "digester", "decomposer", "storage_sac", "vial_holder"
        };
    for (int i = 0; i < machines.length; i++)
      biological(l, b.offset(22 + i * 3, 1, -36), machines[i]);
    for (int x : new int[] {22, 28, 34, 39}) {
      put(l, b, x, 1, -39, Blocks.BOOKSHELF);
      put(l, b, x, 2, -39, Blocks.BOOKSHELF);
      put(l, b, x, 3, -39, Blocks.SKELETON_SKULL);
    }
    biological(l, b.offset(19, 2, -36), "bio_lantern_blue");
    fill(l, b, 24, 0, -31, 37, 0, -29, Blocks.SMOOTH_STONE);
    for (int x : new int[] {26, 31, 36}) {
      fill(l, b, x - 1, 1, -31, x + 1, 4, -29, Blocks.GLASS);
      biological(l, b.offset(x, 1, -30), "packed_flesh");
      biological(l, b.offset(x, 2, -30), "flesh");
    }
  }

  private static void unfinishedCreation(ServerLevel l, BlockPos b) {
    fill(l, b, 23, 0, 1, 37, 0, 24, Blocks.POLISHED_BLACKSTONE_BRICKS);
    fill(l, b, 24, 1, 2, 36, 2, 23, Blocks.CHISELED_STONE_BRICKS);
    fill(l, b, 25, 3, 3, 35, 3, 22, Blocks.SMOOTH_STONE);
    for (int z = 6; z <= 18; z++)
      for (int x = -3; x <= 3; x++) {
        int width = z < 9 ? 2 : 3;
        if (Math.abs(x) <= width) {
          biological(l, b.offset(30 + x, 4, z), "packed_flesh");
          if (Math.abs(x) < width) biological(l, b.offset(30 + x, 5, z), "flesh");
        }
      }
    for (int z = 7; z <= 18; z += 2) {
      fill(l, b, 27, 5, z, 33, 5, z, Blocks.BONE_BLOCK);
      put(l, b, 27, 6, z, Blocks.BONE_BLOCK);
      put(l, b, 33, 6, z, Blocks.BONE_BLOCK);
      biological(l, b.offset(30, 6, z), "fibrous_flesh");
    }
    fill(l, b, 28, 4, 3, 32, 6, 5, Blocks.BONE_BLOCK);
    put(l, b, 29, 6, 2, Blocks.SKELETON_SKULL);
    put(l, b, 31, 6, 2, Blocks.SKELETON_SKULL);
    for (int side : new int[] {-1, 1})
      for (int z : new int[] {9, 16}) {
        biological(l, b.offset(30 + side * 4, 4, z), "packed_flesh");
        put(l, b, 30 + side * 5, 4, z + 1, Blocks.BONE_BLOCK);
      }
    for (int z : new int[] {5, 20})
      for (int x : new int[] {25, 35}) {
        for (int y = 7; y < 29; y++) put(l, b, x, y, z, Blocks.CHAIN);
        put(l, b, x, 29, z, Blocks.SOUL_LANTERN);
      }
  }

  private static void holdingCell(ServerLevel l, BlockPos b, int z) {
    fill(l, b, -42, 0, z - 4, -33, 7, z + 4, Blocks.DEEPSLATE_BRICKS);
    fill(l, b, -41, 0, z - 3, -34, 6, z + 3, Blocks.AIR);
    for (int y = 0; y < 7; y++)
      for (int dz = -3; dz <= 3; dz++) put(l, b, -33, y, z + dz, Blocks.IRON_BARS);
    var door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
    l.setBlock(b.offset(-33, 0, z), door, 2);
    l.setBlock(b.offset(-33, 1, z), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
    put(l, b, -33, 1, z - 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
    l.setBlock(
        b.offset(-32, 1, z - 1),
        Blocks.STONE_BUTTON
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
        2);
    bed(l, b.offset(-39, 0, z + 1), Direction.NORTH, Blocks.WHITE_BED);
    put(l, b, -40, 0, z - 2, Blocks.WATER_CAULDRON);
    put(l, b, -39, 0, z - 2, Blocks.HAY_BLOCK);
    put(l, b, -39, 5, z, Blocks.SEA_LANTERN);
    for (var old :
        l.getEntitiesOfClass(
            Villager.class,
            new AABB(
                Vec3.atLowerCornerOf(b.offset(-42, 0, z - 4)),
                Vec3.atLowerCornerOf(b.offset(-32, 8, z + 5))),
            v -> v.getTags().contains(MARK))) old.discard();
    var villager = EntityType.VILLAGER.create(l);
    if (villager != null) {
      villager.setPos(Vec3.atCenterOf(b.offset(-37, 0, z)));
      villager.setPersistenceRequired();
      villager.addTag(MARK);
      l.addFreshEntity(villager);
    }
  }

  private static void gardens(ServerLevel l, BlockPos b) {
    // The surface landmark is isolated from combat; shrubs and paths frame usable open lawns.
    for (int x = -55; x <= 55; x++)
      for (int z = -55; z <= 55; z++) {
        put(l, b, x, -1, z, Blocks.GRASS_BLOCK);
        for (int y = 0; y < 5; y++) put(l, b, x, y, z, Blocks.AIR);
        int radius = Math.max(Math.abs(x), Math.abs(z));
        if (radius == 47 || radius == 48) put(l, b, x, -1, z, Blocks.MOSSY_STONE_BRICKS);
        if (radius == 49 && Math.floorMod(x + z, 3) != 0) put(l, b, x, 0, z, Blocks.OAK_LEAVES);
        if (radius > 26 && radius < 46 && Math.floorMod(x * 17 + z * 13, 89) == 0)
          put(l, b, x, 0, z, Blocks.ALLIUM);
      }
    for (int x : new int[] {-38, 38})
      for (int z : new int[] {-38, 0, 38}) tree(l, b.offset(x, 0, z));
    for (int x = -34; x <= -23; x++)
      for (int z = 18; z <= 30; z++) {
        double distance = Math.pow((x + 28.5) / 6, 2) + Math.pow((z - 24) / 6.5, 2);
        if (distance < 1) {
          put(l, b, x, -3, z, Blocks.CLAY);
          put(l, b, x, -2, z, Blocks.WATER);
          put(l, b, x, -1, z, Blocks.WATER);
        } else if (distance < 1.35) put(l, b, x, -1, z, Blocks.MOSSY_COBBLESTONE);
      }
    for (int x = -42; x <= 42; x++)
      if (Math.abs(x) > 16) put(l, b, x, -1, -16, Blocks.MOSSY_STONE_BRICKS);
    for (int z = -50; z <= 46; z++)
      for (int x = -1; x <= 1; x++) put(l, b, x, -1, z, Blocks.MOSSY_STONE_BRICKS);
  }

  private static void tree(ServerLevel l, BlockPos p) {
    for (int y = 0; y < 7; y++) l.setBlock(p.above(y), Blocks.DARK_OAK_LOG.defaultBlockState(), 2);
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= 3; z++)
        for (int y = 4; y <= 8; y++)
          if (Math.abs(x) + Math.abs(z) + Math.abs(y - 6) < 6)
            l.setBlock(
                p.offset(x, y, z),
                Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                2);
  }

  private static boolean towerCell(int x, int z, int r) {
    return Math.max(Math.abs(x), Math.abs(z)) <= r && Math.abs(x) + Math.abs(z) <= r + r / 2;
  }

  private static void tower(ServerLevel l, BlockPos b) {
    for (int y = -1; y <= 50; y++)
      for (int x = -14; x <= 14; x++)
        for (int z = -14; z <= 14; z++) {
          if (!towerCell(x, z, 14)) continue;
          boolean edge =
              !towerCell(x - 1, z, 14)
                  || !towerCell(x + 1, z, 14)
                  || !towerCell(x, z - 1, 14)
                  || !towerCell(x, z + 1, 14);
          boolean floor = y == -1 || y == 11 || y == 23 || y == 35 || y == 47;
          Block material = Blocks.AIR;
          if (edge)
            material =
                Math.floorMod(x * 7 + z * 13 + y, 19) < 3
                    ? Blocks.CRACKED_DEEPSLATE_BRICKS
                    : Blocks.POLISHED_BLACKSTONE_BRICKS;
          if (floor) material = edge ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.DARK_OAK_PLANKS;
          if (edge && y % 12 >= 4 && y % 12 <= 8 && (Math.abs(x) <= 2 || Math.abs(z) <= 2))
            material = Blocks.PURPLE_STAINED_GLASS;
          put(l, b, x, y, z, material);
        }
    // Steep stepped roof and bone finials give the tower a dark, uneven silhouette.
    for (int y = 50; y <= 76; y++) {
      int radius = Math.max(0, 16 - (y - 50) * 16 / 26);
      for (int x = -radius; x <= radius; x++)
        for (int z = -radius; z <= radius; z++)
          if (towerCell(x, z, Math.max(1, radius))) put(l, b, x, y, z, Blocks.DEEPSLATE_TILES);
    }
    for (int x : new int[] {-13, 13})
      for (int z : new int[] {-7, 7}) {
        fill(l, b, x - 1, 0, z - 1, x + 1, 53, z + 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
        fill(l, b, x, 54, z, x, 59, z, Blocks.BONE_BLOCK);
        put(l, b, x, 60, z, Blocks.SOUL_LANTERN);
      }
    // Ground floor: receiving hall and throne, with a red approach carpet.
    for (int z = -12; z <= 7; z++)
      for (int x = -1; x <= 1; x++) put(l, b, x, 0, z, Blocks.RED_CARPET);
    fill(l, b, -4, 0, 6, 4, 0, 11, Blocks.CHISELED_POLISHED_BLACKSTONE);
    throne(l, b.offset(0, 1, 9), Direction.NORTH);
    for (int x : new int[] {-5, 5}) {
      put(l, b, x, 0, 9, Blocks.BONE_BLOCK);
      put(l, b, x, 1, 9, Blocks.SKELETON_SKULL);
      put(l, b, x, 2, 9, Blocks.SOUL_LANTERN);
    }
    for (int y = 0; y < 5; y++) for (int x = -2; x <= 2; x++) put(l, b, x, y, -14, Blocks.AIR);
    furnish(l, b);
    // Covered entrance antechamber north of the tower; this becomes world spawn.
    fill(l, b, -7, -1, -37, 7, -1, -16, Blocks.DARK_OAK_PLANKS);
    for (int x = -7; x <= 7; x++)
      for (int z = -37; z <= -16; z++)
        for (int y = 0; y <= 10; y++) {
          boolean shell = Math.abs(x) == 7 || z == -37 || z == -16 || y == 10;
          put(l, b, x, y, z, shell ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR);
        }
    for (int y = 0; y < 4; y++)
      for (int x = -2; x <= 2; x++)
        for (int z : new int[] {-37, -16}) put(l, b, x, y, z, Blocks.AIR);
    for (int z = -35; z <= -18; z += 8)
      for (int x : new int[] {-5, 5}) put(l, b, x, 5, z, Blocks.SOUL_LANTERN);
    for (int x : new int[] {-5, 5}) {
      put(l, b, x, 0, -32, Blocks.LECTERN);
      put(l, b, x, 1, -25, Blocks.SKELETON_SKULL);
    }
    label(
        l,
        Vec3.atLowerCornerOf(b).add(.5, 4, -34),
        "THE DEN OF AZ'KERASH\n"
            + "Observation vault below · private chambers above\n"
            + "Development-world antechamber and spawn");
  }

  private static void furnish(ServerLevel l, BlockPos b) {
    // First floor: ordinary kitchen, dining table and a drawing room for guests.
    fill(l, b, -11, 12, 1, -7, 12, 10, Blocks.SMOOTH_STONE);
    for (int z : new int[] {2, 5}) put(l, b, -10, 13, z, Blocks.FURNACE);
    put(l, b, -10, 13, 8, Blocks.SMOKER);
    put(l, b, -8, 13, 8, Blocks.CAULDRON);
    put(l, b, -8, 13, 2, Blocks.CRAFTING_TABLE);
    put(l, b, -11, 13, 10, Blocks.BARREL);
    put(l, b, -10, 14, 10, Blocks.CHEST);
    fill(l, b, -2, 12, 2, 2, 12, 8, Blocks.DARK_OAK_FENCE);
    fill(l, b, -2, 13, 2, 2, 13, 8, Blocks.DARK_OAK_SLAB);
    for (int z : new int[] {3, 7})
      for (int x : new int[] {-4, 4})
        l.setBlock(
            b.offset(x, 12, z),
            Blocks.DARK_OAK_STAIRS
                .defaultBlockState()
                .setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST),
            2);
    fill(l, b, 6, 12, -7, 10, 12, -5, Blocks.RED_WOOL);
    fill(l, b, 6, 13, -7, 10, 13, -7, Blocks.DARK_OAK_TRAPDOOR);
    fill(l, b, 7, 12, -2, 9, 12, 0, Blocks.DARK_OAK_SLAB);
    put(l, b, 11, 12, -1, Blocks.FLOWER_POT);
    // Study on the second floor, with a central writing desk and a reading alcove.
    for (int x = -10; x <= 10; x++)
      for (int y = 24; y <= 27; y++) if (Math.abs(x) > 3) put(l, b, x, y, 11, Blocks.BOOKSHELF);
    for (int z = -6; z <= 9; z++)
      for (int y = 24; y <= 27; y++) put(l, b, 11, y, z, Blocks.BOOKSHELF);
    fill(l, b, -2, 24, 2, 3, 24, 4, Blocks.DARK_OAK_FENCE);
    fill(l, b, -2, 25, 2, 3, 25, 4, Blocks.DARK_OAK_SLAB);
    put(l, b, 0, 26, 3, Blocks.LECTERN);
    put(l, b, 3, 26, 3, Blocks.LANTERN);
    put(l, b, -4, 24, 3, Blocks.DARK_OAK_STAIRS);
    put(l, b, -10, 24, 8, Blocks.ENCHANTING_TABLE);
    put(l, b, -8, 24, 9, Blocks.CHEST);
    // Master bedroom: double bed, wardrobe, bedside lights and a small seating area.
    bed(l, b.offset(-1, 36, 6), Direction.NORTH, Blocks.RED_BED);
    bed(l, b.offset(0, 36, 6), Direction.NORTH, Blocks.RED_BED);
    for (int x : new int[] {-3, 2}) {
      put(l, b, x, 36, 5, Blocks.BARREL);
      put(l, b, x, 37, 5, Blocks.LANTERN);
    }
    for (int x : new int[] {-3, 2})
      for (int z : new int[] {3, 7}) fill(l, b, x, 36, z, x, 40, z, Blocks.STRIPPED_DARK_OAK_LOG);
    fill(l, b, -3, 41, 3, 2, 41, 7, Blocks.RED_WOOL);
    fill(l, b, 8, 36, 4, 10, 39, 10, Blocks.DARK_OAK_PLANKS);
    put(l, b, -10, 36, 9, Blocks.CHEST);
    put(l, b, -7, 36, 9, Blocks.RED_WOOL);
    put(l, b, -7, 36, 8, Blocks.DARK_OAK_STAIRS);
    for (int y : new int[] {0, 12, 24, 36, 48})
      for (int x : new int[] {-9, 9})
        for (int z : new int[] {-10, 10}) put(l, b, x, y + 6, z, Blocks.LANTERN);
  }

  /** Incremental repair: preserves Junior, inventories and the surrounding estate. */
  public static void repairAccessAndLighting(ServerLevel l) {
    var s = surface(l);
    var b = base(l);
    // Remove the old crossed-tread staircase and restore the storeys it perforated.
    fill(l, s, -12, 0, -11, -4, 49, -3, Blocks.AIR);
    for (int y : new int[] {-1, 11, 23, 35, 47})
      for (int x = -12; x <= -4; x++)
        for (int z = -11; z <= -3; z++)
          if (towerCell(x, z, 13)) put(l, s, x, y, z, Blocks.DARK_OAK_PLANKS);
    DenStaircase.build(l, s.offset(-6, 0, -5), 48, false);
    DenStaircase.build(
        l, new BlockPos(s.getX(), b.getY(), s.getZ() - 30), s.getY() - b.getY(), true);
    // Restore only the tower envelope; staircase clearance must never cut the facade.
    for (int y = 0; y <= 49; y++)
      for (int x = -14; x <= 14; x++)
        for (int z = -14; z <= 14; z++) {
          if (!towerCell(x, z, 14)) continue;
          boolean edge =
              !towerCell(x - 1, z, 14)
                  || !towerCell(x + 1, z, 14)
                  || !towerCell(x, z - 1, 14)
                  || !towerCell(x, z + 1, 14);
          if (!edge || Math.abs(x) <= 2 && z == -14 && y < 5) continue;
          Block material =
              Math.floorMod(x * 7 + z * 13 + y, 19) < 3
                  ? Blocks.CRACKED_DEEPSLATE_BRICKS
                  : Blocks.POLISHED_BLACKSTONE_BRICKS;
          if (y % 12 == 11) material = Blocks.CHISELED_POLISHED_BLACKSTONE;
          int side = Math.abs(x) == 14 ? z : Math.abs(z) == 14 ? x : 99;
          if (Math.abs(side) <= 2 && y % 12 >= 4 && y % 12 <= 9)
            material =
                (Math.abs(side) <= 1 && y % 12 <= 7 || side == 0 && y % 12 == 8)
                    ? Blocks.PURPLE_STAINED_GLASS
                    : Blocks.CHISELED_POLISHED_BLACKSTONE;
          put(l, s, x, y, z, material);
        }
    roads(l, s);
    supportFrame(l, b);
    interiorLighting(l, s, b);
    for (int x : new int[] {-38, 38})
      for (int z : new int[] {-35, 0, 35}) put(l, b, x, 0, z, Blocks.CHISELED_POLISHED_BLACKSTONE);
    // Replace the inverted chandelier chains with ceiling-anchored pendant lights.
    for (int x : new int[] {25, 35})
      for (int z : new int[] {5, 20}) {
        for (int y = 7; y < 36; y++) {
          var at = b.offset(x, y, z);
          var old = l.getBlockState(at);
          if (old.is(Blocks.CHAIN) || old.getBlock() instanceof LanternBlock)
            put(l, b, x, y, z, Blocks.AIR);
        }
        var lamp = b.offset(x, 12, z);
        var anchor = lamp.above();
        while (anchor.getY() < b.getY() + 36 && l.getBlockState(anchor).isAir())
          anchor = anchor.above();
        for (var q = lamp.above(); q.getY() < anchor.getY(); q = q.above())
          l.setBlock(q, Blocks.CHAIN.defaultBlockState(), 2);
        l.setBlock(
            lamp, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
      }
    supportLights(l, b, s);
    put(l, b, 19, 0, -36, Blocks.POLISHED_BLACKSTONE_BRICKS);
    put(l, b, 19, 1, -36, Blocks.BONE_BLOCK);
    put(l, b, 37, 1, -37, Blocks.CHISELED_POLISHED_BLACKSTONE);
    var rackBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:vial_holder"));
    var rack = l.getBlockState(b.offset(37, 1, -36));
    if (!rack.is(rackBlock)) rack = rackBlock.defaultBlockState();
    rack = rack.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
    l.setBlock(b.offset(37, 1, -36), rack, 3);
    for (var old :
        l.getEntitiesOfClass(
            net.minecraft.world.entity.Display.TextDisplay.class,
            new AABB(
                Vec3.atLowerCornerOf(b.offset(-43, 0, -43)),
                Vec3.atLowerCornerOf(b.offset(43, 36, 43))),
            DevelopmentDisplayPolicy::obsolete)) old.discard();

    // Replace the old billboard embedded in the stairwell's north wall.
    for (var display :
        l.getEntitiesOfClass(
            net.minecraft.world.entity.Display.TextDisplay.class,
            new AABB(
                Vec3.atLowerCornerOf(s.offset(-10, 0, -40)),
                Vec3.atLowerCornerOf(s.offset(10, 12, -15))),
            e -> e.getTags().contains(MARK) && textContains(e, "THE DEN OF AZ'KERASH")))
      display.discard();
    var display = net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(l);
    if (display != null) {
      var tag = new CompoundTag();
      tag.putString(
          "text",
          Component.Serializer.toJson(
              Component.literal(
                  "THE DEN OF AZ'KERASH\nObservation vault below\nPrivate chambers above"),
              l.registryAccess()));
      tag.putString("billboard", "fixed");
      tag.putString("alignment", "center");
      tag.putInt("line_width", 150);
      tag.putInt("background", 0x55000000);
      var brightness = new CompoundTag();
      brightness.putInt("block", 15);
      brightness.putInt("sky", 15);
      tag.put("brightness", brightness);
      display.load(tag);
      display.setYRot(180);
      display.setPos(s.getX() + .5, s.getY() + 2.3, s.getZ() - 36.2);
      display.addTag(MARK);
      l.addFreshEntity(display);
    }
    var state = CoreTestState.get(l);
    state.denAccessVersion = 1;
    state.setDirty();
  }

  private static boolean textContains(
      net.minecraft.world.entity.Display.TextDisplay display, String text) {
    return display.saveWithoutId(new CompoundTag()).getString("text").contains(text);
  }

  private static void supportFrame(ServerLevel l, BlockPos b) {
    int[][] old = {
      {-27, -27},
      {27, -27},
      {-27, 27},
      {27, 27},
      {-27, 0},
      {-14, 27},
      {14, 27},
      {14, -27},
      {-14, -27}
    };
    for (var p : old) fill(l, b, p[0] - 2, 0, p[1] - 3, p[0] + 2, 29, p[1] + 2, Blocks.AIR);
    fill(l, b, -42, 29, -42, 42, 35, 42, Blocks.AIR);
    // Continuous perimeter bearing ring; the roof web rests directly on it.
    for (int radius : new int[] {23, 35, 42})
      for (int n = -radius; n <= radius; n++)
        for (int sign : new int[] {-1, 1}) {
          fill(l, b, n, 29, sign * radius, n, 35, sign * radius, Blocks.POLISHED_BLACKSTONE_BRICKS);
          fill(l, b, sign * radius, 29, n, sign * radius, 35, n, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
    for (int radius : new int[] {23, 35})
      for (var side : Direction.Plane.HORIZONTAL) {
        int[] offsets =
            radius == 23 ? new int[] {-23, -12, 12, 23} : new int[] {-35, -18, 0, 18, 35};
        var tangent = side.getClockWise();
        for (int n : offsets) {
          int x = side.getStepX() * radius + tangent.getStepX() * n,
              z = side.getStepZ() * radius + tangent.getStepZ() * n;
          // Leave the lab, specimen slab, cells and stair entrances clear.
          if (radius == 35
              && (z == -35 && Math.abs(x) < 6
                  || x == -35 && z > -32 && z < 7
                  || x == 35 && z >= -2 && z <= 25)) continue;
          column(l, b, x, z);
          fill(l, b, x - 1, 29, z - 1, x + 1, 31, z + 1, Blocks.CHISELED_DEEPSLATE);
        }
      }
    for (var side : Direction.Plane.HORIZONTAL)
      for (int offset : new int[] {-23, -12, 12, 23}) {
        var tangent = side.getClockWise();
        for (int r = 23; r <= 42; r++) {
          int y = 29 + (int) Math.round(3 * Math.sin(Math.PI * (r - 23) / 19d));
          int x = side.getStepX() * r + tangent.getStepX() * offset,
              z = side.getStepZ() * r + tangent.getStepZ() * offset;
          fill(l, b, x, y, z, x, 35, z, Blocks.DEEPSLATE_BRICKS);
        }
      }
  }

  private static void supportLights(ServerLevel l, BlockPos b, BlockPos s) {
    for (int x = -42; x <= 42; x++)
      for (int z = -42; z <= 42; z++)
        for (int y = 0; y < 36; y++) {
          var p = b.offset(x, y, z);
          var state = l.getBlockState(p);
          if (!(state.getBlock() instanceof LanternBlock)) continue;
          if (state.getValue(LanternBlock.HANGING)
              && l.getBlockState(p.above())
                  .isFaceSturdy(l, p.above(), Direction.DOWN, SupportType.CENTER)) continue;
          if (l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) continue;
          // Small attached bone brackets support column lamps; otherwise hang from the ceiling.
          boolean bracket = false;
          for (var side : Direction.Plane.HORIZONTAL) {
            var wall = p.relative(side);
            if (l.getBlockState(wall).isFaceSturdy(l, wall, side.getOpposite())) {
              l.setBlock(p.below(), Blocks.BONE_BLOCK.defaultBlockState(), 2);
              bracket = true;
              break;
            }
          }
          if (!bracket) {
            var ceiling = p.above();
            while (ceiling.getY() < b.getY() + 36 && l.getBlockState(ceiling).isAir())
              ceiling = ceiling.above();
            if (!l.getBlockState(ceiling).isAir()) {
              for (var q = p.above(); q.getY() < ceiling.getY(); q = q.above())
                l.setBlock(q, Blocks.CHAIN.defaultBlockState(), 2);
              l.setBlock(p, state.setValue(LanternBlock.HANGING, true), 2);
            } else l.setBlock(p.below(), Blocks.BONE_BLOCK.defaultBlockState(), 2);
          }
        }
    for (int x = -13; x <= 13; x++)
      for (int z = -13; z <= 13; z++)
        for (int y = 0; y < 50; y++) {
          var p = s.offset(x, y, z);
          var state = l.getBlockState(p);
          if (!(state.getBlock() instanceof LanternBlock)
              || l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) continue;
          var ceiling = p.above();
          while (ceiling.getY() < s.getY() + 50 && l.getBlockState(ceiling).isAir())
            ceiling = ceiling.above();
          if (l.getBlockState(ceiling).isAir()) continue;
          for (var q = p.above(); q.getY() < ceiling.getY(); q = q.above())
            l.setBlock(q, Blocks.CHAIN.defaultBlockState(), 2);
          l.setBlock(p, state.setValue(LanternBlock.HANGING, true), 2);
        }
  }

  private static void interiorLighting(ServerLevel l, BlockPos s, BlockPos b) {
    // Recessed lights under carpet keep the large vault navigable, rather than relying on dim soul
    // lights.
    for (int x = -39; x <= 39; x += 6)
      for (int z = -39; z <= 39; z += 6) {
        var p = b.offset(x, 0, z);
        if (Math.abs(x) < 18 && Math.abs(z) < 18 || !l.getBlockState(p).isAir()) continue;
        put(l, b, x, -1, z, Blocks.SHROOMLIGHT);
        put(l, b, x, 0, z, Blocks.PURPLE_CARPET);
      }
    for (int x = -12; x <= 12; x += 6)
      for (int z = -12; z <= 12; z += 6) {
        var p = b.offset(x, 0, z);
        if (l.getBlockState(p).isAir()) {
          put(l, b, x, -1, z, Blocks.SHROOMLIGHT);
          put(l, b, x, 0, z, Blocks.MOSS_CARPET);
        }
        put(l, b, x, 15, z, Blocks.SEA_LANTERN);
      }
    for (int floor : new int[] {0, 12, 24, 36})
      for (int x : new int[] {-10, -4, 4, 10})
        for (int z : new int[] {-10, -4, 4, 10}) {
          var p = s.offset(x, floor, z);
          if (!towerCell(x, z, 13)
              || x >= -11 && x <= -1 && z >= -10 && z <= 0
              || !l.getBlockState(p).isAir()) continue;
          put(l, s, x, floor - 1, z, Blocks.SHROOMLIGHT);
          put(l, s, x, floor, z, Blocks.PURPLE_CARPET);
        }
    for (int z = -36; z <= -17; z += 4)
      for (int x : new int[] {-6, 6}) {
        put(l, s, x, 2, z, Blocks.SHROOMLIGHT);
      }
  }

  public static void roads(ServerLevel l, BlockPos b) {
    int y = b.getY() - 1;
    road(l, new BlockPos(-1084, y, -128), new BlockPos(1792, y, -128));
    road(l, new BlockPos(-1024, y, -1064), new BlockPos(-1084, y, -1064));
    road(l, new BlockPos(-1084, y, -1064), new BlockPos(-1084, y, -128));
    var state = CoreTestState.get(l);
    var exhibits =
        java.util.List.of(
            state.layout().base(),
            state.arrowOrigin,
            state.potionOrigin,
            state.digestionOrigin,
            VisionFixture.base(l),
            MembraneFixture.base(l),
            BrainFixture.base(l),
            LocomotionFixture.base(l),
            ModularLimbFixture.base(l));
    for (var e : exhibits)
      road(l, new BlockPos(e.getX(), y, -128), new BlockPos(e.getX(), y, e.getZ() - 12));
    road(l, new BlockPos(1408, y, -128), new BlockPos(1408, y, 230));
    road(l, new BlockPos(1536, y, -128), new BlockPos(1536, y, 232));
    road(l, new BlockPos(1792, y, -128), new BlockPos(1792, y, 1006));
    road(l, new BlockPos(1792, y, 1006), new BlockPos(1024, y, 1006));
  }

  private static void road(ServerLevel l, BlockPos a, BlockPos b) {
    int length = Math.abs(b.getX() - a.getX()) + Math.abs(b.getZ() - a.getZ());
    int dx = Integer.signum(b.getX() - a.getX()), dz = Integer.signum(b.getZ() - a.getZ());
    for (int i = 0; i <= length; i++) {
      var p = a.offset(dx * i, 0, dz * i);
      for (int w = -2; w <= 2; w++) {
        var at = p.offset(dz == 0 ? 0 : w, 0, dx == 0 ? 0 : w);
        l.setBlock(at, DevelopmentTestBlocks.PATH.get().defaultBlockState(), 2);
        for (int h = 1; h <= 3; h++) l.setBlock(at.above(h), Blocks.AIR.defaultBlockState(), 2);
      }
      if (i % 32 == 0) {
        var lamp = p.offset(dz == 0 ? 0 : 4, 1, dx == 0 ? 0 : 4);
        int hx = dz == 0 ? 0 : -1, hz = dx == 0 ? 0 : -1;
        for (int up = 0; up < 4; up++)
          l.setBlock(lamp.above(up), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 3);
        for (int arm = 1; arm <= 2; arm++)
          l.setBlock(
              lamp.offset(hx * arm, 3, hz * arm),
              Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(),
              3);

        for (int up = 0; up < 4; up++) {
          var q = lamp.above(up);
          l.setBlock(q, Block.updateFromNeighbourShapes(l.getBlockState(q), l, q), 2);
        }
        for (int arm = 1; arm <= 2; arm++) {
          var q = lamp.offset(hx * arm, 3, hz * arm);
          l.setBlock(q, Block.updateFromNeighbourShapes(l.getBlockState(q), l, q), 2);
        }
        l.setBlock(
            lamp.offset(hx * 2, 2, hz * 2),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
            3);
      }
    }
  }

  private static void bed(ServerLevel l, BlockPos foot, Direction face, Block bed) {
    var state = bed.defaultBlockState().setValue(BedBlock.FACING, face);
    l.setBlock(foot, state, 2);
    l.setBlock(foot.relative(face), state.setValue(BedBlock.PART, BedPart.HEAD), 2);
  }

  private static void throne(ServerLevel l, BlockPos p, Direction facing) {
    var state =
        ComponentRegistry.THRONE_OF_AZKERASH
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    l.setBlock(p, state, 2);
    l.setBlock(
        p.above(),
        state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
        2);
  }

  private static void label(ServerLevel l, Vec3 at, String text) {
    var display = EntityType.TEXT_DISPLAY.create(l);
    if (display == null) return;
    var tag = new CompoundTag();
    tag.putString("text", Component.Serializer.toJson(Component.literal(text), l.registryAccess()));
    tag.putString("billboard", "center");
    tag.putString("alignment", "center");
    tag.putInt("line_width", 320);
    tag.putInt("background", 0x66000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(at);
    display.addTag(MARK);
    l.addFreshEntity(display);
  }

  /** Incremental architectural pass; does not rebuild rooms, erase inventories or move subjects. */
  public static void detailExterior(ServerLevel l, BlockPos b) {
    // Battlemented entrance hall: solid parapet below alternating merlons.
    for (int x = -8; x <= 8; x++)
      for (int z = -38; z <= -15; z++) {
        if (Math.abs(x) != 8 && z != -38 && z != -15) continue;
        put(l, b, x, 10, z, Blocks.CHISELED_POLISHED_BLACKSTONE);
        put(l, b, x, 11, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        if (Math.floorMod(Math.abs(x) == 8 ? z : x, 3) != 1)
          put(l, b, x, 12, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
      }
    for (int x : new int[] {-8, 8})
      for (int z : new int[] {-35, -29, -23, -17}) {
        fill(l, b, x, 0, z - 1, x, 7, z + 1, Blocks.DEEPSLATE_BRICKS);
        put(l, b, x, 8, z, Blocks.CHISELED_DEEPSLATE);
        if (z != -29)
          for (int y = 3; y <= 6; y++)
            put(l, b, Integer.signum(x) * 7, y, z + 2, Blocks.PURPLE_STAINED_GLASS);
      }
    for (int x : new int[] {-4, 4})
      fill(l, b, x, 0, -38, x, 5, -38, Blocks.CHISELED_POLISHED_BLACKSTONE);
    fill(l, b, -3, 6, -38, 3, 6, -38, Blocks.CHISELED_DEEPSLATE);
    fill(l, b, -1, 7, -38, 1, 7, -38, Blocks.BONE_BLOCK);
    for (int x : new int[] {-3, 3})
      l.setBlock(
          b.offset(x, 4, -38),
          Blocks.RED_WALL_BANNER
              .defaultBlockState()
              .setValue(WallBannerBlock.FACING, Direction.NORTH),
          2);
    for (int x : new int[] {-8, 8})
      for (int z : new int[] {-38, -15}) {
        fill(l, b, x, 11, z, x, 15, z, Blocks.CHISELED_POLISHED_BLACKSTONE);
        put(l, b, x, 16, z, Blocks.SOUL_LANTERN);
      }
    // Projecting cornices break the tower into furnished storeys.
    for (int y : new int[] {-1, 11, 23, 35, 47})
      for (int x = -15; x <= 15; x++)
        for (int z = -15; z <= 15; z++)
          if (towerCell(x, z, 15) && !towerCell(x, z, 14))
            put(l, b, x, y, z, Blocks.CHISELED_DEEPSLATE);
    for (var face : Direction.Plane.HORIZONTAL)
      for (int side : new int[] {-5, 5}) {
        var tangent = face.getClockWise();
        int x = face.getStepX() * 15 + tangent.getStepX() * side,
            z = face.getStepZ() * 15 + tangent.getStepZ() * side;
        fill(l, b, x, 0, z, x, 49, z, Blocks.DEEPSLATE_BRICKS);
        put(l, b, x, 50, z, Blocks.BONE_BLOCK);
        put(l, b, x, 51, z, Blocks.SOUL_LANTERN);
      }
    for (int floor : new int[] {0, 12, 24, 36})
      for (var face : Direction.Plane.HORIZONTAL) {
        var tangent = face.getClockWise();
        for (int side = -2; side <= 2; side++)
          for (int y = 4; y <= 9; y++) {
            int x = face.getStepX() * 14 + tangent.getStepX() * side,
                z = face.getStepZ() * 14 + tangent.getStepZ() * side;
            boolean opening = Math.abs(side) <= 1 && y <= 7 || side == 0 && y == 8;
            put(
                l,
                b,
                x,
                floor + y,
                z,
                opening ? Blocks.PURPLE_STAINED_GLASS : Blocks.CHISELED_POLISHED_BLACKSTONE);
          }
      }
    // A deliberate, complete steep roof, plus a visible central finial.
    for (int y = 50; y <= 76; y++) {
      int radius = Math.max(0, 16 - (y - 50) * 16 / 26);
      for (int x = -radius; x <= radius; x++)
        for (int z = -radius; z <= radius; z++)
          if (towerCell(x, z, Math.max(1, radius))) put(l, b, x, y, z, Blocks.DEEPSLATE_TILES);
    }
    fill(l, b, 0, 77, 0, 0, 80, 0, Blocks.BONE_BLOCK);
    put(l, b, 0, 81, 0, Blocks.SOUL_LANTERN);
    wildGarden(l, b);
  }

  private static int gardenHeight(int x, int z) {
    return Math.max(
        0,
        Math.min(2, (int) Math.round(.9 * Math.sin(x * .22 + z * .11) + .8 * Math.cos(z * .28))));
  }

  private static void wildGarden(ServerLevel l, BlockPos b) {
    var random = new java.util.Random(739141L);
    var plants =
        new Block[] {
          Blocks.FERN,
          Blocks.SHORT_GRASS,
          Blocks.ALLIUM,
          Blocks.LILY_OF_THE_VALLEY,
          Blocks.AZURE_BLUET,
          Blocks.DANDELION,
          Blocks.RED_MUSHROOM,
          Blocks.BROWN_MUSHROOM
        };
    for (int x = -46; x <= 46; x++)
      for (int z = -46; z <= 46; z++) {
        if (Math.max(Math.abs(x), Math.abs(z)) < 20
            || Math.abs(x) <= 3
            || Math.abs(x) < 11 && z < -13
            || x >= -36 && x <= -20 && z >= 15 && z <= 33) continue;
        int h = gardenHeight(x, z);
        for (int y = 0; y <= 2; y++)
          if (l.getBlockState(b.offset(x, y, z)).getBlock() instanceof BushBlock)
            put(l, b, x, y, z, Blocks.AIR);
        for (int y = 0; y < h; y++)
          put(l, b, x, y, z, y == h - 1 ? Blocks.MOSS_BLOCK : Blocks.DIRT);
        if (h == 0 && Math.floorMod(x * 31 + z * 17, 13) < 3)
          put(l, b, x, -1, z, Blocks.COARSE_DIRT);
        var at = b.offset(x, h, z);
        if (!l.getBlockState(at).isAir()) continue;
        int pick = random.nextInt(14);
        if (pick < 6) {
          var plant = plants[random.nextInt(plants.length)];
          if (plant == Blocks.RED_MUSHROOM || plant == Blocks.BROWN_MUSHROOM)
            put(l, b, x, h - 1, z, Blocks.PODZOL);
          l.setBlock(at, plant.defaultBlockState(), 2);
        } else if (pick == 8) {
          l.setBlock(
              at,
              Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
              2);
          if (random.nextBoolean())
            l.setBlock(
                at.above(),
                Blocks.FLOWERING_AZALEA_LEAVES
                    .defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, true),
                2);
        }
      }
    // Food/herb plots are small, irregular patches among the overgrowth.
    for (int x = 24; x <= 30; x++)
      for (int z = -7; z <= -1; z++)
        if (Math.pow((x - 27) / 3.5, 2) + Math.pow((z + 4) / 3.5, 2) < 1) {
          int h = gardenHeight(x, z);
          put(l, b, x, h - 1, z, Blocks.SOUL_SAND);
          l.setBlock(
              b.offset(x, h, z),
              Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3),
              2);
        }
    for (int x = 25; x <= 31; x++)
      for (int z = 14; z <= 18; z++)
        if ((x - 28) * (x - 28) + (z - 16) * (z - 16) < 13) {
          for (int y = 0; y <= 2; y++) put(l, b, x, y, z, Blocks.AIR);
          if (x == 28 && z == 16) put(l, b, x, -1, z, Blocks.WATER);
          else {
            l.setBlock(
                b.offset(x, -1, z),
                Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7),
                2);
            l.setBlock(
                b.offset(x, 0, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 2);
          }
        }
    int[][] stones = {{-27, -12}, {29, -33}, {39, 9}, {-42, 16}, {14, 38}};
    for (var c : stones)
      for (int x = -3; x <= 3; x++)
        for (int z = -2; z <= 2; z++)
          for (int y = 0; y < 3; y++)
            if (x * x / 9d + z * z / 4d + y * y / 6d < 1)
              put(
                  l,
                  b,
                  c[0] + x,
                  gardenHeight(c[0], c[1]) + y,
                  c[1] + z,
                  (x + y + z) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
    // Ruined foundations, partial arches and fallen pieces share the tower's masonry.
    for (var c : new int[][] {{-37, -30}, {29, 28}, {-19, 37}}) {
      int h = gardenHeight(c[0], c[1]);
      for (int x = 0; x <= 9; x++)
        for (int z = 0; z <= 7; z++)
          if (x == 0 || x == 9 || z == 0 || z == 7)
            put(l, b, c[0] + x, h - 1, c[1] + z, Blocks.CRACKED_DEEPSLATE_BRICKS);
      for (int x = 0; x <= 8; x++)
        for (int y = 0; y < 1 + Math.floorMod(x * 7 + c[0], 4); y++)
          put(
              l,
              b,
              c[0] + x,
              h + y,
              c[1],
              x % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_DEEPSLATE_BRICKS);
      fill(l, b, c[0], h, c[1], c[0] + 1, h + 5, c[1] + 1, Blocks.CHISELED_POLISHED_BLACKSTONE);
      fill(l, b, c[0] + 5, h, c[1], c[0] + 6, h + 2, c[1] + 1, Blocks.MOSSY_STONE_BRICKS);
      put(l, b, c[0] + 2, h + 5, c[1], Blocks.DEEPSLATE_BRICK_SLAB);
      put(l, b, c[0] + 3, h + 4, c[1] + 1, Blocks.DEEPSLATE_BRICK_STAIRS);
      for (int rubble = 0; rubble < 7; rubble++) {
        int x = c[0] + random.nextInt(10), z = c[1] + 2 + random.nextInt(5);
        put(
            l,
            b,
            x,
            gardenHeight(x, z),
            z,
            rubble % 2 == 0 ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.COBBLESTONE_STAIRS);
      }
    }
    // A broken outer tower arc and toppled buttress suggest the surviving tower
    // once belonged to a much larger keep. Keep both away from the approach road.
    for (int x = 19; x <= 35; x++)
      for (int z = -34; z <= -18; z++) {
        double radius = Math.hypot(x - 27, z + 26);
        if (radius < 6 || radius > 8 || x > 29 && z < -27) continue;
        int height = 1 + Math.floorMod(x * 13 + z * 7, 4);
        for (int y = -1; y < height; y++)
          put(
              l,
              b,
              x,
              gardenHeight(x, z) + y,
              z,
              Math.floorMod(x + z + y, 4) == 0
                  ? Blocks.CHISELED_DEEPSLATE
                  : Blocks.CRACKED_DEEPSLATE_BRICKS);
      }
    for (int step = 0; step < 10; step++) {
      int x = -35 + step, z = 10 + step / 2;
      int h = gardenHeight(x, z);
      put(l, b, x, h, z, Blocks.CHISELED_POLISHED_BLACKSTONE);
      if (step < 7) put(l, b, x, h + 1, z, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
      if (step % 3 == 0) put(l, b, x, h, z + 1, Blocks.MOSSY_STONE_BRICK_SLAB);
    }
    for (int x = -41; x <= -34; x++)
      l.setBlock(
          b.offset(x, gardenHeight(x, 34), 34),
          Blocks.DARK_OAK_LOG
              .defaultBlockState()
              .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
          2);
    // Vines cling to actual supported facade faces, not invisible floating sheets.
    for (var face : Direction.Plane.HORIZONTAL)
      for (int tangent : new int[] {-6, -3, 2, 6}) {
        var side = face.getClockWise();
        int x = face.getStepX() * 15 + side.getStepX() * tangent,
            z = face.getStepZ() * 15 + side.getStepZ() * tangent;
        var property =
            switch (face.getOpposite()) {
              case NORTH -> BlockStateProperties.NORTH;
              case SOUTH -> BlockStateProperties.SOUTH;
              case EAST -> BlockStateProperties.EAST;
              default -> BlockStateProperties.WEST;
            };
        for (int y = 1; y < 12 + Math.floorMod(tangent * 5, 16); y++) {
          var p = b.offset(x, y, z);
          var support = p.relative(face.getOpposite());
          if (l.getBlockState(p).isAir() && l.getBlockState(support).isFaceSturdy(l, support, face))
            l.setBlock(p, Blocks.VINE.defaultBlockState().setValue(property, true), 2);
        }
      }
  }
}
