package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Decorative, once-built habitat spaces; no creature spawning or life-support exemptions. */
public final class DevelopmentHabitatFixtures {
  public static final int AQUARIUM_HALF_WIDTH = 24, AQUARIUM_HALF_LENGTH = 18, WATER_HEIGHT = 18;

  private DevelopmentHabitatFixtures() {}

  public static BlockPos aquariumBase(ServerLevel level) {
    return new BlockPos(1536, CoreTestState.get(level).layout().base().getY(), 256);
  }

  private static void keepLoaded(ServerLevel l, BlockPos base, int x, int z) {
    for (int cx = (base.getX() - x) >> 4; cx <= (base.getX() + x) >> 4; cx++) {
      for (int cz = (base.getZ() - z) >> 4; cz <= (base.getZ() + z) >> 4; cz++) {
        l.setChunkForced(cx, cz, true);
      }
    }
  }

  public static void installAquarium(ServerLevel l) {
    var state = CoreTestState.get(l);
    if (state.isAquariumBuilt()) {
      return;
    }
    buildAquarium(l, aquariumBase(l));
    state.setAquariumBuilt(true);
    state.setDirty();
  }

  public static void buildAquarium(ServerLevel l, BlockPos b) {
    int w = AQUARIUM_HALF_WIDTH, d = AQUARIUM_HALF_LENGTH;
    keepLoaded(l, b, w + 4, d + 5);
    var glass = DevelopmentTestBlocks.GLASS.get().defaultBlockState();
    for (int x = -w - 3; x <= w + 3; x++) {
      for (int z = -d - 3; z <= d + 3; z++) {
        boolean inside = Math.abs(x) < w && Math.abs(z) < d;
        l.setBlock(b.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        l.setBlock(
            b.offset(x, 0, z),
            (inside
                    ? (Math.floorMod(x * 13 + z * 7, 11) < 2 ? Blocks.GRAVEL : Blocks.SAND)
                    : Blocks.DARK_PRISMARINE)
                .defaultBlockState(),
            2);
        if (Math.abs(x) > w || Math.abs(z) > d) {
          continue;
        }
        for (int y = 1; y <= 23; y++) {
          boolean shell = Math.abs(x) == w || Math.abs(z) == d || y == 23;
          boolean rib =
              shell
                  && (y == 1
                      || y == 19
                      || y == 23 && (Math.abs(x) == w || Math.abs(z) == d)
                      || (Math.abs(x) == w && z % 9 == 0)
                      || (Math.abs(z) == d && x % 12 == 0));
          l.setBlock(
              b.offset(x, y, z),
              shell
                  ? (rib ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : glass)
                  : (y <= WATER_HEIGHT ? Blocks.WATER : Blocks.AIR).defaultBlockState(),
              2);
        }
      }
    }
    // Low perimeter gardens leave the large central swimming volume unobstructed.
    for (int x = -w + 2; x < w - 1; x++) {
      for (int z = -d + 2; z < d - 1; z++) {
        if (Math.abs(x) < w - 6 && Math.abs(z) < d - 6) {
          continue;
        }
        int noise = Math.floorMod(x * 37 + z * 19, 31);
        if (noise < 4) {
          l.setBlock(b.offset(x, 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
        } else if (noise == 6) {
          for (int y = 1; y <= 3; y++) {
            l.setBlock(b.offset(x, y, z), Blocks.KELP_PLANT.defaultBlockState(), 2);
          }
          l.setBlock(b.offset(x, 4, z), Blocks.KELP.defaultBlockState(), 2);
        }
        if (Math.floorMod(x, 8) == 0 && Math.floorMod(z, 6) == 0) {
          l.setBlock(b.offset(x, 0, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        }
      }
    }
    for (int side : new int[] {-1, 1}) {
      int cx = side * 17, cz = 8;
      for (int x = -3; x <= 3; x++) {
        for (int z = -3; z <= 3; z++) {
          for (int y = 1; y <= 3; y++) {
            if (x * x + z * z + 3 * y * y < 20) {
              l.setBlock(
                  b.offset(cx + x, y, cz + z), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
            }
          }
        }
      }
      l.setBlock(b.offset(cx, 1, cz - 4), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(), 2);
      l.setBlock(b.offset(cx + 1, 1, cz - 4), Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), 2);
    }
    // Dry ladder and viewing deck enter above the waterline, so access cannot drain the tank.
    for (int y = 1; y <= 21; y++) {
      l.setBlock(b.offset(-25, y, -20), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 2);
      l.setBlock(
          b.offset(-25, y, -21),
          Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH),
          2);
    }
    for (int x = -26; x <= -20; x++) {
      for (int z = -21; z <= -16; z++) {
        l.setBlock(b.offset(x, 20, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
      }
    }
    for (int x = -23; x <= -20; x++) {
      for (int y = 21; y <= 22; y++) {
        l.setBlock(b.offset(x, y, -d), Blocks.AIR.defaultBlockState(), 2);
      }
    }
    label(
        l,
        Vec3.atLowerCornerOf(b).add(.5, 3, -d - 3.2),
        "AQUATIC HOMUNCULUS HABITAT\n"
            + "47 × 35 interior · 18 blocks of water\n"
            + "Ready for your aquatic creature · no subject installed\n"
            + "Dry ladder at the left leads to the swimming deck.",
        "bioships.aquarium");
  }

  public static void buildJuniorPen(ServerLevel l, BlockPos b) {
    var fixtureState = CoreTestState.get(l);
    fixtureState.setJuniorHabitatBuilt(true);
    fixtureState.setJuniorHabitatOrigin(b.immutable());
    fixtureState.setDirty();
    int half = 17;
    keepLoaded(l, b, half + 3, half + 4);
    var glass = DevelopmentTestBlocks.GLASS.get().defaultBlockState();
    for (int x = -half - 2; x <= half + 2; x++) {
      for (int z = -half - 2; z <= half + 2; z++) {
        boolean inside = Math.abs(x) < half && Math.abs(z) < half;
        l.setBlock(
            b.offset(x, -1, z),
            (inside ? Blocks.MOSS_BLOCK : Blocks.MOSSY_STONE_BRICKS).defaultBlockState(),
            2);
        if (Math.abs(x) > half || Math.abs(z) > half) {
          continue;
        }
        for (int y = 0; y <= 15; y++) {
          boolean shell = Math.abs(x) == half || Math.abs(z) == half || y == 15;
          boolean rib =
              shell
                  && (y == 0
                      || (y == 15 && (Math.abs(x) == half || Math.abs(z) == half))
                      || (Math.abs(x) == half && Math.abs(z) == half));
          l.setBlock(
              b.offset(x, y, z),
              shell
                  ? (rib ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : glass)
                  : Blocks.AIR.defaultBlockState(),
              2);
        }
      }
    }
    for (int side : new int[] {-1, 1}) {
      for (int end : new int[] {-1, 1}) {
        var trunk = b.offset(side * 12, 0, end * 12);
        for (int y = 0; y < 5; y++) {
          l.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        for (int x = -2; x <= 2; x++) {
          for (int z = -2; z <= 2; z++) {
            for (int y = 3; y <= 6; y++) {
              if (Math.abs(x) + Math.abs(z) + Math.abs(y - 4) < 5 && (x != 0 || z != 0 || y >= 5)) {
                l.setBlock(
                    trunk.offset(x, y, z),
                    Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                    2);
              }
            }
          }
        }
      }
    }
    for (int x = -14; x <= 14; x++) {
      for (int z = -14; z <= 14; z++) {
        if (Math.abs(x) < 10 && Math.abs(z) < 10) {
          continue;
        }
        if (Math.floorMod(x * 13 + z * 7, 29) == 0) {
          l.setBlock(
              b.offset(x, 0, z), (x % 2 == 0 ? Blocks.FERN : Blocks.POPPY).defaultBlockState(), 2);
        }
        if (Math.floorMod(x * 7 + z * 11, 53) == 0) {
          l.setBlock(b.offset(x, -1, z), Blocks.SHROOMLIGHT.defaultBlockState(), 2);
        }
      }
    }
    for (int x = -13; x <= -10; x++) {
      for (int z = -5; z <= 1; z++) {
        l.setBlock(b.offset(x, -2, z), Blocks.CLAY.defaultBlockState(), 2);
        l.setBlock(b.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 2);
      }
    }
    for (int x = 0; x <= 1; x++) {
      var door =
          DevelopmentTestBlocks.GLASS_DOOR
              .get()
              .defaultBlockState()
              .setValue(DoorBlock.FACING, Direction.SOUTH)
              .setValue(DoorBlock.HINGE, x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
      l.setBlock(b.offset(x, 0, -half), door, 2);
      l.setBlock(b.offset(x, 1, -half), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
      for (int z = -half - 2; z <= -half + 4; z++) {
        l.setBlock(b.offset(x, -1, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
      }
    }
  }

  private static void label(ServerLevel l, Vec3 at, String text, String mark) {
    for (var old :
        l.getEntitiesOfClass(
            Display.TextDisplay.class,
            new AABB(at, at).inflate(4),
            e -> e.getTags().contains(mark))) {
      old.discard();
    }
    var display = EntityType.TEXT_DISPLAY.create(l);
    if (display == null) {
      return;
    }
    var tag = new CompoundTag();
    tag.putString("text", Component.Serializer.toJson(Component.literal(text), l.registryAccess()));
    tag.putString("billboard", "center");
    tag.putString("alignment", "center");
    tag.putInt("line_width", 340);
    tag.putInt("background", 0x66000000);
    tag.putBoolean("shadow", true);
    display.load(tag);
    display.setPos(at);
    display.addTag(mark);
    l.addFreshEntity(display);
  }
}
