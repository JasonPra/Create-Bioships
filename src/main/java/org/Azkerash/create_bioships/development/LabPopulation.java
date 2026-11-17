package org.Azkerash.create_bioships.development;

import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.BioshipIntegration;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Explicit scenario populations, independent of natural spawning's light/player requirements. */
public final class LabPopulation {
  static final String MARKER = "BioshipEcosystemMob";
  private static final Map<ServerLevel, Long> LAST =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static BlockPos controller(BlockPos center) {
    return center.above(12);
  }

  static void install(ServerLevel level, BlockPos center) {
    var pos = controller(center);
    level.setBlock(pos, Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState(), 3);
    if (!(level.getBlockEntity(pos) instanceof CommandBlockEntity block))
      throw new IllegalStateException("Could not install ecosystem controller at " + pos);
    block.getCommandBlock().setCommand("bioship_ecosystem pulse");
    block.getCommandBlock().setTrackOutput(false);
    block.setAutomatic(true);
    block.setChanged();
  }

  static AABB landBounds(ServerLevel level, BlockPos center) {
    return new AABB(
        center.getX() - EcosystemLab.RADIUS,
        level.getMinBuildHeight(),
        center.getZ() - EcosystemLab.RADIUS,
        center.getX() + EcosystemLab.RADIUS + 1,
        level.getMaxBuildHeight(),
        center.getZ() + EcosystemLab.RADIUS + 1);
  }

  public record Counts(int fish, int prey, int hostiles, int cores) {}

  public static Counts counts(ServerLevel level, BlockPos center) {
    var mobs = level.getEntitiesOfClass(Mob.class, landBounds(level, center));
    int cores = 0;
    for (var entry :
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getAllSubLevels())
      if (entry instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ship && !ship.isRemoved())
        for (var actor : BioshipIntegration.blockEntities(ship))
          if (actor instanceof LivingCoreBlockEntity core && !core.body().dead()) {
            var point = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
            if (landBounds(level, center).contains(point) || AquaticLab.contains(level, point))
              cores++;
          }
    return new Counts(
        AquaticLab.fishCount(level),
        (int) mobs.stream().filter(m -> m instanceof Animal).count(),
        (int) mobs.stream().filter(m -> m instanceof Monster).count(),
        cores);
  }

  static void pulse(ServerLevel level, EcosystemLab.State state) {
    long now = level.getGameTime();
    if (Objects.equals(LAST.put(level, now), now)) return;
    if (now % 200 == 0) AquaticLab.replenish(level);
    if (now % 600 == 0) spawnLand(level, state.center, false);
    if (now % 1200 == 0) spawnLand(level, state.center, true);
  }

  static final long REFILL_INTERVAL = 6000;

  public static boolean refillDue(EcosystemLab.State state, long now) {
    if (state.nextRefill <= 0) {
      state.nextRefill = now + REFILL_INTERVAL;
      state.setDirty();
      return false;
    }
    if (now < state.nextRefill) return false;
    state.nextRefill = now + REFILL_INTERVAL;
    state.setDirty();
    return true;
  }

  public static void replenish(ServerLevel level, EcosystemLab.State state) {
    var counts = counts(level, state.center);
    for (int i = counts.fish(); i < AquaticLab.FISH_CAP; i++) AquaticLab.replenish(level);
    for (int i = counts.prey(); i < EcosystemLab.PREY_CAP; i++)
      if (!spawnLand(level, state.center, false)) break;
    for (int i = counts.hostiles(); i < EcosystemLab.HOSTILE_CAP; i++)
      if (!spawnLand(level, state.center, true)) break;
  }

  static void prime(ServerLevel level, BlockPos center) {
    for (int i = 0; i < 6; i++) spawnLand(level, center, false);
    for (int i = 0; i < 2; i++) spawnLand(level, center, true);
  }

  static boolean spawnLand(ServerLevel level, BlockPos center, boolean hostile) {
    var bounds = landBounds(level, center);
    int count =
        level
            .getEntitiesOfClass(
                Mob.class, bounds, m -> hostile ? m instanceof Monster : m instanceof Animal)
            .size();
    if (count >= (hostile ? EcosystemLab.HOSTILE_CAP : EcosystemLab.PREY_CAP)) return false;
    List<EntityType<? extends Mob>> choices =
        hostile
            ? List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER)
            : List.of(EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN);
    var type = choices.get(level.random.nextInt(choices.size()));
    for (int i = 0; i < 32; i++) {
      int x = center.getX() + level.random.nextInt(100) - 50;
      int z = center.getZ() + level.random.nextInt(100) - 50;
      level.getChunk(x >> 4, z >> 4);
      var pos =
          level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
      if (!level.getFluidState(pos).isEmpty()
          || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
        continue;
      var mob = type.create(level);
      if (mob == null) return false;
      mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.random.nextFloat() * 360, 0);
      if (!level.noCollision(mob, mob.getBoundingBox())) continue;
      net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(
          mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
      EcosystemLab.sunProtection(mob);
      mob.setPersistenceRequired();
      mob.getPersistentData().putBoolean(MARKER, true);
      return level.addFreshEntity(mob);
    }
    return false;
  }
}
