package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.components.logic.respiration.RespiratoryBlock;

/** One oxygen reserve per creature; cached organ census, sampled once per second. */
@EventBusSubscriber(modid = "create_bioships")
public final class SubLevelOxygen {
  public static final int MAXIMUM = 1200, SUPPLY_PER_ORGAN = 40, SUFFOCATION_INTERVAL = 20;
  public static final double REFERENCE_MASS = 100;

  public static double demandForMass(double mass) {
    return 20 * Math.max(1, Math.max(0, mass) / REFERENCE_MASS);
  }

  private static final Map<ServerSubLevel, Map<BlockPos, BlockState>> ORGANS = new WeakHashMap<>();

  public record Status(
      double current,
      int maximum,
      double demand,
      double supply,
      int lungs,
      int gills,
      int breathingLungs,
      int breathingGills) {}

  private SubLevelOxygen() {}

  static void initialize(ServerSubLevel body, Collection<Long> cells) {
    var organs = new HashMap<BlockPos, BlockState>();
    for (long cell : cells) {
      var p = BlockPos.of(cell);
      var s = body.getLevel().getBlockState(p);
      if (s.getBlock() instanceof RespiratoryBlock) {
        organs.put(p, s);
      }
    }
    ORGANS.put(body, organs);
  }

  static void changed(ServerSubLevel body, BlockPos p, BlockState state) {
    var organs = ORGANS.get(body);
    if (organs == null) {
      return;
    }
    if (state.getBlock() instanceof RespiratoryBlock) {
      organs.put(p.immutable(), state);
    } else {
      organs.remove(p);
    }
  }

  /** Lung needs no exposed port. Gills need water directly outside an unobstructed intake. */
  public static boolean breathing(ServerSubLevel body, BlockPos p) {
    var level = body.getLevel();
    var state = level.getBlockState(p);
    if (!(state.getBlock() instanceof RespiratoryBlock organ)) {
      return false;
    }
    if (!organ.gills()) {
      var at = BlockPos.containing(body.logicalPose().transformPosition(Vec3.atCenterOf(p)));
      return !state.getValue(BlockStateProperties.WATERLOGGED)
          && level.hasChunkAt(at)
          && !level.getFluidState(at).is(FluidTags.WATER);
    }
    var face = state.getValue(BlockStateProperties.FACING);
    var neighbor = p.relative(face);
    var local = level.getBlockState(neighbor);
    if (!local.getCollisionShape(level, neighbor).isEmpty()) {
      return false;
    }
    var from = body.logicalPose().transformPosition(Vec3.atCenterOf(p).relative(face, .505));
    var to = body.logicalPose().transformPosition(Vec3.atCenterOf(p).relative(face, .62));
    if (!level.hasChunkAt(BlockPos.containing(from))
        || !level.hasChunkAt(BlockPos.containing(to))) {
      return false;
    }
    if (level
            .clip(
                new net.minecraft.world.level.ClipContext(
                    from,
                    to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()))
            .getType()
        != net.minecraft.world.phys.HitResult.Type.MISS) {
      return false;
    }
    if (level.getFluidState(neighbor).is(FluidTags.WATER)) {
      return true;
    }
    var at =
        BlockPos.containing(
            body.logicalPose().transformPosition(Vec3.atCenterOf(p).relative(face, .6)));
    return level.hasChunkAt(at)
        && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
        && level.getFluidState(at).is(FluidTags.WATER);
  }

  public static Status status(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    int lungs = 0, gills = 0, breathingLungs = 0, breathingGills = 0;
    double supply = 0;
    for (var part : SubLevelCreatureParts.members(body)) {
      if (!ORGANS.containsKey(part)) {
        SubLevelHealth.blocks(part);
      } // Initialize only once after load.
      for (var e : ORGANS.getOrDefault(part, Map.of()).entrySet()) {
        var organ = (RespiratoryBlock) e.getValue().getBlock();
        if (organ.gills()) {
          gills++;
        } else {
          lungs++;
        }
        if (breathing(part, e.getKey())) {
          supply += SUPPLY_PER_ORGAN;
          if (organ.gills()) {
            breathingGills++;
          } else {
            breathingLungs++;
          }
        }
      }
    }
    return new Status(
        SubLevelData.oxygen(body).value(),
        MAXIMUM,
        demandForMass(SubLevelHunger.metabolism(body).mass()),
        supply,
        lungs,
        gills,
        breathingLungs,
        breathingGills);
  }

  public static void advance(ServerSubLevel body, int ticks) {
    if (ticks <= 0
        || !SubLevelCreatureParts.available(body)
        || SubLevelCreatureParts.isPart(body)
        || !SubLevelData.isLiving(body)
        || SubLevelHealth.status(body).dead()) {
      return;
    }
    var state = SubLevelData.oxygen(body);
    if (state.paused()) {
      return;
    }
    var sample = status(body);
    double air = state.value();
    int suffocation = state.suffocationTicks();
    for (int remaining = ticks; remaining > 0; ) {
      int step = Math.min(remaining, 20);
      remaining -= step;
      // Existing reserve covers this interval; newly depleted air starts the damage clock next
      // step.
      boolean empty = air <= 0;
      air = Math.clamp(air + (sample.supply() - sample.demand()) * step / 20.0, 0, MAXIMUM);
      if (empty && air == 0) {
        suffocation += step;
      } else {
        suffocation = 0;
      }
      if (suffocation >= SUFFOCATION_INTERVAL) {
        suffocation -= SUFFOCATION_INTERVAL;
        SubLevelHealth.damage(body, 2, "suffocation");
        if (SubLevelHealth.status(body).dead()) {
          break;
        }
      }
    }
    var next = new SubLevelData.Oxygen(air, suffocation, false);
    if (!next.equals(state)) {
      SubLevelData.oxygen(body, next);
    }
  }

  private record Gill(ServerSubLevel body, BlockPos pos, boolean wet) {}

  /** All gills share one creature-wide phase, even across linked physical bodies. */
  public static void animate(ServerSubLevel body, long now) {
    if (SubLevelCreatureParts.isPart(body) || !SubLevelCreatureParts.available(body)) {
      return;
    }
    var gills = new ArrayList<Gill>();
    boolean gasping = false, airBreathing = false;
    for (var part : SubLevelCreatureParts.members(body)) {
      var level = part.getLevel();
      for (var e : ORGANS.getOrDefault(part, Map.of()).entrySet()) {
        if (!((RespiratoryBlock) e.getValue().getBlock()).gills()) {
          airBreathing |= breathing(part, e.getKey());
          continue;
        }
        var face = e.getValue().getValue(BlockStateProperties.FACING);
        var local = e.getKey().relative(face);
        var at =
            BlockPos.containing(
                part.logicalPose()
                    .transformPosition(Vec3.atCenterOf(e.getKey()).relative(face, .6)));
        boolean wet =
            level.getFluidState(local).is(FluidTags.WATER)
                || (level.hasChunkAt(at) && level.getFluidState(at).is(FluidTags.WATER));
        gills.add(new Gill(part, e.getKey(), wet));
        gasping |= !wet;
      }
    }
    if (gills.isEmpty()) {
      return;
    }
    gasping &= !airBreathing;
    boolean active = SubLevelData.isLiving(body) && !SubLevelHealth.status(body).dead();
    int phase =
        active ? new int[] {0, 1, 2, 3, 2, 1}[(int) Math.floorMod(now / (gasping ? 3 : 12), 6)] : 0;
    for (var gill : gills) {
      var level = gill.body().getLevel();
      var state = level.getBlockState(gill.pos());
      boolean gasp = active && !gill.wet() && !airBreathing;
      int gillPhase = active && !gill.wet() && airBreathing ? 0 : phase;
      if (state.getValue(RespiratoryBlock.BREATH) != gillPhase
          || state.getValue(RespiratoryBlock.GASPING) != gasp) {
        level.setBlock(
            gill.pos(),
            state
                .setValue(RespiratoryBlock.BREATH, gillPhase)
                .setValue(RespiratoryBlock.GASPING, gasp),
            2);
      }
    }
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
    long now = e.getServer().getTickCount();
    if (now % 3 == 0) {
      for (var body : new ArrayList<>(ORGANS.keySet())) {
        if (!body.isRemoved() && !SubLevelCreatureParts.isPart(body)) {
          animate(body, now);
        }
      }
    }
    if (e.getServer().getTickCount() % 20 != 0) {
      return;
    }
    for (var level : e.getServer().getAllLevels()) {
      var c = SubLevelContainer.getContainer(level);
      if (c == null) {
        continue;
      }
      for (var entry : c.getAllSubLevels()) {
        if (entry instanceof ServerSubLevel body) {
          advance(body, 20);
        }
      }
    }
    ORGANS.keySet().removeIf(ServerSubLevel::isRemoved);
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
    ORGANS.clear();
  }
}
