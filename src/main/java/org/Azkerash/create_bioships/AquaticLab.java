package org.Azkerash.create_bioships;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/** Enclosed water fixture, separate from the unchanged natural terrain inside the land arena. */
final class AquaticLab {
  static final int RADIUS = 20, DEPTH = 12, AIR_HEIGHT = 8, FISH_CAP = 16;

  private static final class State extends SavedData {
    boolean built;
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
                    state.center = BlockPos.of(tag.getLong("Center"));
                    return state;
                  }),
              "bioship_aquatic_lab");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      tag.putBoolean("Built", built);
      tag.putLong("Center", center.asLong());
      return tag;
    }
  }

  static BlockPos center(ServerLevel level) {
    return State.get(level).center;
  }

  static void build(ServerLevel level, BlockPos landCenter) {
    var state = State.get(level);
    if (state.built) return;
    int x = landCenter.getX() + 144, z = landCenter.getZ();
    level.getChunk(x >> 4, z >> 4);
    var center =
        new BlockPos(
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
    for (int dx : new int[] {-12, 12})
      for (int dz : new int[] {-12, 12}) {
        var pos = center.offset(dx, -DEPTH + 1, dz);
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner))
          throw new IllegalStateException(
              "Aquatic spawner placement failed at " + pos + " state=" + level.getBlockState(pos));
        spawner.setEntityId(EntityType.COD, level.random);
        var data = spawner.saveWithFullMetadata(level.registryAccess());
        data.putShort("MinSpawnDelay", (short) 200);
        data.putShort("MaxSpawnDelay", (short) 800);
        data.putShort("SpawnCount", (short) 2);
        data.putShort("MaxNearbyEntities", (short) 6);
        data.putShort("RequiredPlayerRange", (short) 64);
        data.putShort("SpawnRange", (short) 6);
        spawner.loadWithComponents(data, level.registryAccess());
      }
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
    state.setDirty();
    for (int i = 0; i < 8; i++) spawnFish(level, center);
  }

  private static AABB bounds(BlockPos center) {
    return new AABB(
        net.minecraft.world.phys.Vec3.atLowerCornerOf(center.offset(-RADIUS, -DEPTH, -RADIUS)),
        net.minecraft.world.phys.Vec3.atLowerCornerOf(center.offset(RADIUS, AIR_HEIGHT, RADIUS)));
  }

  static void tick(ServerLevel level) {
    var state = State.get(level);
    if (!state.built || level.getGameTime() % 200 != 0) return;
    var center = state.center;
    if (level.getNearestPlayer(center.getX(), center.getY(), center.getZ(), 64, false) != null)
      return;
    boolean creature =
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
            .getAllSubLevels()
            .stream()
            .filter(ship -> ship instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel)
            .map(ship -> (dev.ryanhcode.sable.sublevel.ServerSubLevel) ship)
            .flatMap(ship -> BioshipIntegration.blockEntities(ship).stream())
            .anyMatch(
                entity ->
                    entity instanceof LivingCoreBlockEntity core
                        && !core.body().dead()
                        && bounds(center).contains(BioshipBlueprint.physical(core, BlockPos.ZERO)));
    if (creature
        && level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.animal.AbstractFish.class, bounds(center))
                .size()
            < FISH_CAP) spawnFish(level, center);
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
    if (level.noCollision(fish, fish.getBoundingBox())) level.addFreshEntity(fish);
  }
}
