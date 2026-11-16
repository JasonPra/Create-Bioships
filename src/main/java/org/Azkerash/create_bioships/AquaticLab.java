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
  static final int RADIUS = 20, DEPTH = 12, AIR_HEIGHT = 8, FISH_CAP = 16;

  static final int ISOLATION_RADIUS = RADIUS + 4;

  private static final class State extends SavedData {
    boolean built, isolated;
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
                    state.center = BlockPos.of(tag.getLong("Center"));
                    return state;
                  }),
              "bioship_aquatic_lab");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      tag.putBoolean("Built", built);
      tag.putBoolean("Isolated", isolated);
      tag.putLong("Center", center.asLong());
      return tag;
    }
  }

  static BlockPos center(ServerLevel level) {
    return State.get(level).center;
  }

  static void build(ServerLevel level, BlockPos landCenter) {
    var state = State.get(level);
    if (state.built) {
      if (!state.isolated) {
        isolate(level, state.center);
        state.isolated = true;
        state.setDirty();
      }
      return;
    }
    int x = landCenter.getX() + 144, z = landCenter.getZ();
    level.getChunk(x >> 4, z >> 4);
    var center =
        !state.center.equals(BlockPos.ZERO)
            ? state.center
            : new BlockPos(
                x,
                Math.clamp(
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2,
                    level.getMinBuildHeight() + DEPTH + 2,
                    level.getMaxBuildHeight() - AIR_HEIGHT - 2),
                z);
    for (int dx = -RADIUS; dx <= RADIUS; dx++)
      for (int dz = -RADIUS; dz <= RADIUS; dz++)
        for (int y = -DEPTH; y <= AIR_HEIGHT; y++) {
          boolean boundary = Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS || y == AIR_HEIGHT;
          level.setBlock(
              center.offset(dx, y, dz),
              (y == -DEPTH
                      ? Blocks.STONE
                      : boundary ? Blocks.GLASS : y < 0 ? Blocks.WATER : Blocks.AIR)
                  .defaultBlockState(),
              2);
        }
    for (int dx = -18; dx <= -15; dx++)
      for (int dz = -2; dz <= 2; dz++)
        level.setBlock(center.offset(dx, 1, dz), Blocks.STONE.defaultBlockState(), 2);
    int n = 0;
    for (var id : new String[] {"bio_forge", "decomposer", "digester"}) {
      level.setBlock(
          center.offset(-4 + n++ * 4, -DEPTH + 1, 0),
          BuiltInRegistries.BLOCK.get(ResourceLocation.parse("biomancy:" + id)).defaultBlockState(),
          3);
    }
    for (int side : new int[] {-1, 1}) {
      var pos = center.offset(side * 14, -DEPTH + 1, side * 14);
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
    state.center = center;
    isolate(level, center);
    state.isolated = true;
    state.setDirty();
    for (int i = 0; i < 8; i++) spawnFish(level, center);
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
  }

  private static void spawnFish(ServerLevel level, BlockPos center) {
    var pos =
        center.offset(
            level.random.nextInt(30) - 15,
            -2 - level.random.nextInt(8),
            level.random.nextInt(30) - 15);
    if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return;
    var fish = EntityType.COD.create(level);
    if (fish == null) return;
    fish.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 0, 0);
    if (level.noCollision(fish, fish.getBoundingBox())) {
      fish.setPersistenceRequired();
      fish.getPersistentData().putBoolean(LabPopulation.MARKER, true);
      level.addFreshEntity(fish);
    }
  }
}
