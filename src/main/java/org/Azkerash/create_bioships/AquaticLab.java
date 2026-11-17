package org.Azkerash.create_bioships;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/** Enclosed water fixture, separate from the unchanged natural terrain inside the land arena. */
final class AquaticLab {
  static final int RADIUS = 80, DEPTH = 24, AIR_HEIGHT = 8, FISH_CAP = 128;
  private static final int LEGACY_RADIUS = 20, VERSION = 2;

  static final int ISOLATION_RADIUS = RADIUS + 4;

  private static final class State extends SavedData {
    boolean built, isolated;
    int version;
    BlockPos center = BlockPos.ZERO;

    static State get(ServerLevel level) {
      return level
          .getDataStorage()
          .computeIfAbsent(
              new Factory<>(
                  State::new,
                  (tag, lookup) -> {
                    var state = new State();
                    state.built = tag.getBoolean("Built");
                    state.isolated = tag.getBoolean("Isolated");
                    state.version = tag.getInt("Version");
                    state.center = BlockPos.of(tag.getLong("Center"));
                    return state;
                  }),
              "bioship_aquatic_lab");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      tag.putBoolean("Built", built);
      tag.putBoolean("Isolated", isolated);
      tag.putInt("Version", version);
      tag.putLong("Center", center.asLong());
      return tag;
    }
  }

  static BlockPos center(ServerLevel level) {
    return State.get(level).center;
  }

  static void build(ServerLevel level, BlockPos landCenter) {
    var state = State.get(level);
    if (state.built && state.version >= VERSION) {
      if (!state.isolated) {
        isolate(level, state.center);
        state.isolated = true;
        state.setDirty();
      }
      return;
    }
    boolean upgrading = state.built;
    int x = landCenter.getX() + 256, z = landCenter.getZ();
    level.getChunk(x >> 4, z >> 4);
    var center =
        !state.center.equals(BlockPos.ZERO)
            ? state.center
            : new BlockPos(
                x,
                Math.clamp(
                    NavigationLab.lab(level)
                        ? landCenter.getY() + 4
                        : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2,
                    level.getMinBuildHeight() + DEPTH + 2,
                    level.getMaxBuildHeight() - AIR_HEIGHT - 2),
                z);
    for (int dx = -RADIUS; dx <= RADIUS; dx++)
      for (int dz = -RADIUS; dz <= RADIUS; dz++) {
        // Preserve existing inner fixtures, inventory and shallow shelf on version upgrades.
        if (upgrading && Math.abs(dx) < LEGACY_RADIUS && Math.abs(dz) < LEGACY_RADIUS) continue;
        int floor = OceanLabTerrain.floor(dx, dz);
        for (int y = -DEPTH; y <= AIR_HEIGHT; y++) {
          boolean boundary = Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS || y == AIR_HEIGHT;
          var block =
              y == -DEPTH
                  ? Blocks.STONE
                  : boundary
                      ? Blocks.GLASS
                      : y < floor
                          ? Blocks.STONE
                          : y == floor
                              ? OceanLabTerrain.sediment(dx, dz)
                              : y < 0 ? Blocks.WATER : Blocks.AIR;
          if (!boundary && y > floor && y < 0) {
            var rock = OceanLabTerrain.rock(dx, y, dz);
            if (rock != null) block = rock;
          }
          var pos = center.offset(dx, y, dz);
          if (upgrading && level.getBlockEntity(pos) != null) continue;
          level.setBlock(pos, block.defaultBlockState(), 2);
        }
      }
    plants(level, center, upgrading);
    for (int dx = -RADIUS + 2; dx <= -RADIUS + 5; dx++)
      for (int dz = -2; dz <= 2; dz++)
        level.setBlock(center.offset(dx, 1, dz), Blocks.STONE.defaultBlockState(), 2);
    // An upgrade keeps the original caches and introduces the deeper outer course.
    if (!upgrading) {
      int n = 0;
      for (var id : new String[] {"bio_forge", "decomposer", "digester"}) {
        int dx = -8 + n++ * 8;
        level.setBlock(
            center.offset(dx, OceanLabTerrain.floor(dx, 0) + 1, 0),
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("biomancy:" + id))
                .defaultBlockState(),
            3);
      }
    }
    for (int side : new int[] {-1, 1}) {
      int delta = side * (RADIUS - 10);
      var pos = center.offset(delta, OceanLabTerrain.floor(delta, delta) + 1, delta);
      level.setBlock(
          pos,
          BuiltInRegistries.BLOCK
              .get(ResourceLocation.parse("biomancy:storage_sac"))
              .defaultBlockState(),
          3);
      var inv =
          level.getCapability(
              net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
      if (inv != null) {
        net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
            inv, new ItemStack(Items.PORKCHOP, 64), false);
        for (var id : new String[] {"flesh_bits", "elastic_fibers", "bone_fragments"})
          net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
              inv,
              new ItemStack(
                  BuiltInRegistries.ITEM.get(ResourceLocation.parse("biomancy:" + id)), 64),
              false);
      }
    }
    state.built = true;
    state.version = VERSION;
    state.center = center;
    isolate(level, center);
    state.isolated = true;
    state.setDirty();
    if (!upgrading) for (int i = 0; i < FISH_CAP / 2; i++) spawnFish(level, center);
  }

  static BlockPos viewpoint(ServerLevel level) {
    return center(level).offset(-RADIUS + 4, 2, 0);
  }

  private static void plants(ServerLevel level, BlockPos center, boolean upgrading) {
    for (int x = -RADIUS + 2; x < RADIUS - 1; x++)
      for (int z = -RADIUS + 2; z < RADIUS - 1; z++) {
        if (upgrading && Math.abs(x) < LEGACY_RADIUS && Math.abs(z) < LEGACY_RADIUS) continue;
        int pattern = OceanLabTerrain.pattern(x, z);
        int floor = OceanLabTerrain.floor(x, z);
        if (!level
            .getBlockState(center.offset(x, floor, z))
            .isFaceSturdy(level, center.offset(x, floor, z), Direction.UP)) continue;
        var root = center.offset(x, floor + 1, z);
        if (!level.getBlockState(root).is(Blocks.WATER)) continue;
        if (pattern % 19 == 0) {
          int height = Math.min(-floor - 2, 3 + pattern % 11);
          int clear = 0;
          while (clear < height && level.getBlockState(root.above(clear)).is(Blocks.WATER)) clear++;
          for (int y = 0; y < clear; y++) {
            var p = root.above(y);
            level.setBlock(
                p, (y == clear - 1 ? Blocks.KELP : Blocks.KELP_PLANT).defaultBlockState(), 2);
          }
        } else if (pattern % 4 == 0) level.setBlock(root, Blocks.SEAGRASS.defaultBlockState(), 2);
      }
  }

  /** Spawn-proof viewing apron and opaque outer barrier; the glass tank and water stay intact. */
  static void isolate(ServerLevel level, BlockPos center) {
    var slab =
        Blocks.STONE_SLAB
            .defaultBlockState()
            .setValue(
                SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
    for (int dx = -ISOLATION_RADIUS; dx <= ISOLATION_RADIUS; dx++)
      for (int dz = -ISOLATION_RADIUS; dz <= ISOLATION_RADIUS; dz++) {
        int edge = Math.max(Math.abs(dx), Math.abs(dz));
        if (edge <= RADIUS) continue;
        if (edge == ISOLATION_RADIUS) {
          for (int y = -DEPTH; y <= AIR_HEIGHT + 1; y++)
            level.setBlock(center.offset(dx, y, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
          level.setBlock(center.offset(dx, AIR_HEIGHT + 2, dz), slab, 2);
        } else {
          // A decorative seabed diorama behind the glass, isolated from the live habitat.
          int relief = -DEPTH + 2 + Math.floorMod(dx * 7 + dz * 11, 5);
          for (int y = -DEPTH; y < -2; y++)
            level.setBlock(
                center.offset(dx, y, dz),
                (y < relief
                        ? Blocks.STONE
                        : y == relief
                            ? Blocks.SAND
                            : y == relief + 1 && Math.floorMod(dx + dz, 5) == 0
                                ? Blocks.PRISMARINE
                                : Blocks.WATER)
                    .defaultBlockState(),
                2);
          level.setBlock(center.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
          level.setBlock(center.offset(dx, -1, dz), slab, 2);
          for (int y = 0; y <= AIR_HEIGHT + 2; y++)
            level.setBlock(center.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 2);
        }
      }
    // Animals already beside the old glass should not remain trapped inside the new apron.
    var band =
        new AABB(
            net.minecraft.world.phys.Vec3.atLowerCornerOf(
                center.offset(-ISOLATION_RADIUS, -DEPTH, -ISOLATION_RADIUS)),
            net.minecraft.world.phys.Vec3.atLowerCornerOf(
                center.offset(ISOLATION_RADIUS + 1, AIR_HEIGHT + 3, ISOLATION_RADIUS + 1)));
    for (var mob :
        level.getEntitiesOfClass(
            Mob.class,
            band,
            m ->
                m instanceof net.minecraft.world.entity.animal.Animal
                    || m instanceof net.minecraft.world.entity.monster.Monster)) {
      if (bounds(center).contains(mob.position())) continue;
      for (var face : Direction.Plane.HORIZONTAL) {
        var outside = center.relative(face, ISOLATION_RADIUS + 4);
        level.getChunk(outside.getX() >> 4, outside.getZ() >> 4);
        var ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outside);
        if (!level.getFluidState(ground.below()).isEmpty()
            || !level
                .getBlockState(ground.below())
                .isFaceSturdy(level, ground.below(), Direction.UP)) continue;
        mob.moveTo(
            ground.getX() + .5, ground.getY(), ground.getZ() + .5, mob.getYRot(), mob.getXRot());
        break;
      }
    }
  }

  private static AABB bounds(BlockPos center) {
    return new AABB(
        net.minecraft.world.phys.Vec3.atLowerCornerOf(center.offset(-RADIUS, -DEPTH, -RADIUS)),
        net.minecraft.world.phys.Vec3.atLowerCornerOf(center.offset(RADIUS, AIR_HEIGHT, RADIUS)));
  }

  static int fishCount(ServerLevel level) {
    return level
        .getEntitiesOfClass(
            net.minecraft.world.entity.animal.AbstractFish.class, bounds(center(level)))
        .size();
  }

  static void replenish(ServerLevel level) {
    var state = State.get(level);
    if (!state.built) return;
    if (level
            .getEntitiesOfClass(
                net.minecraft.world.entity.animal.AbstractFish.class, bounds(state.center))
            .size()
        < FISH_CAP) spawnFish(level, state.center);
  }

  static boolean contains(ServerLevel level, net.minecraft.world.phys.Vec3 point) {
    return bounds(center(level)).contains(point);
  }

  static void reset(ServerLevel level, BlockPos landCenter) {
    for (var fish :
        level.getEntitiesOfClass(
            net.minecraft.world.entity.animal.AbstractFish.class, bounds(center(level))))
      fish.discard();
    State.get(level).built = false;
    State.get(level).isolated = false;
    build(level, landCenter);
    for (var item :
        level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, bounds(center(level))))
      item.discard();
  }

  private record SpawnSites(long tick, BlockPos center, java.util.List<BlockPos> sites) {}

  private static final java.util.Map<ServerLevel, SpawnSites> SPAWN_SITES =
      new java.util.WeakHashMap<>();

  private static void spawnFish(ServerLevel level, BlockPos center) {
    var sampled = SPAWN_SITES.get(level);
    if (sampled == null
        || sampled.tick() != level.getGameTime()
        || !sampled.center().equals(center)) {
      var sites = new java.util.ArrayList<BlockPos>();
      for (int x = -RADIUS + 2; x < RADIUS - 1; x += 4)
        for (int z = -RADIUS + 2; z < RADIUS - 1; z += 4) {
          var pos = center.offset(x, OceanLabTerrain.floor(x, z) + 2, z);
          if (level.isPositionEntityTicking(pos)
              && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) sites.add(pos);
        }
      sampled = new SpawnSites(level.getGameTime(), center, sites);
      SPAWN_SITES.put(level, sampled);
    }
    if (sampled.sites().isEmpty()) return;
    for (int attempt = 0; attempt < 16; attempt++) {
      var base = sampled.sites().get(level.random.nextInt(sampled.sites().size()));
      var pos = base.above(level.random.nextInt(Math.max(1, center.getY() - base.getY() - 1)));
      if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) continue;
      var type =
          switch (level.random.nextInt(4)) {
            case 0 -> EntityType.SALMON;
            case 1 -> EntityType.TROPICAL_FISH;
            default -> EntityType.COD;
          };
      var fish = type.create(level);
      if (fish == null) return;
      fish.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 0, 0);
      if (!level.noCollision(fish, fish.getBoundingBox())) continue;
      fish.setPersistenceRequired();
      fish.getPersistentData().putBoolean(LabPopulation.MARKER, true);
      level.addFreshEntity(fish);
      return;
    }
  }
}
