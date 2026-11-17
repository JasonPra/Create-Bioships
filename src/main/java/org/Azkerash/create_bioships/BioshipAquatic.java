package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Water propulsion is separate from flight; breathing remains a finite survival resource. */
public final class BioshipAquatic {
  private int air = 300;
  private long lastTick = Long.MIN_VALUE;
  private volatile boolean wet, swimming;
  private boolean waterContact;
  private boolean breathing = true, airAccess = true, seekingAir;
  private final ExposureHistory gillUsage = new ExposureHistory();

  ExposureHistory gillUsage() {
    return gillUsage;
  }

  private volatile Vec3 velocity = Vec3.ZERO;

  private final LegUsageHistory legUsage = new LegUsageHistory();
  private long nextLegEvolution;

  LegUsageHistory legUsage() {
    return legUsage;
  }

  long nextLegEvolution() {
    return nextLegEvolution;
  }

  void legEvolved(long next) {
    nextLegEvolution = next;
  }

  public boolean wet() {
    return wet;
  }

  boolean waterContact() {
    return waterContact || wet;
  }

  public boolean swimming() {
    return swimming;
  }

  public int air() {
    return air;
  }

  boolean needsAir() {
    return seekingAir && (breathing || !airAccess);
  }

  void tick(LivingCoreBlockEntity core) {
    if (core.getLevel().getGameTime() == lastTick) return;
    lastTick = core.getLevel().getGameTime();
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var level = core.getLevel();
    int immersed = 0, total = 0;
    for (var cell : BioshipDecay.blocks(core.ship())) {
      total++;
      var point = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(cell));
      if (level.getFluidState(BlockPos.containing(point)).is(FluidTags.WATER)
          && !aboveFluid(core, point)) immersed++;
    }
    wet = immersed > 0 && immersed * 4 >= total;
    if (level.getGameTime() % 20 == 0) {
      waterContact = touchesWater(core);
      legUsage.observe(waterContact);
      gillUsage.observe(waterContact);
    }
    airAccess = BioshipRespiration.airAccess(core);
    breathing = canBreathe(core);
    if (!breathing) {
      air = Math.max(0, air - 1);
      if (air == 0 && level.getGameTime() % 20 == 0) {
        if (airAccess) core.body().asphyxiate(core);
        else core.body().drown(core);
      }
    } else air = Math.min(300, air + 4);
    if (!airAccess && !breathing && air < 200) seekingAir = true;
    if (air == 300) seekingAir = false;
    swimming =
        (wet || seekingAir && breathing && waterWithinReach(core, here))
            && !core.body().dead()
            && (core.canFly() || core.crawl().carriedLoad(core) <= core.crawl().loadCapacity());
    velocity = Vec3.ZERO;
    if (swimming) {
      if (seekingAir && breathing) {
        // Stay braced at the waterline until air is replenished instead of diving after one breath.
        if (level.getGameTime() % 20 == 0) core.setChanged();
        return;
      }
      var selected = core.brain().lastDecision();
      Vec3 direction =
          selected != null && selected.point() != null && !needsAir()
              ? selected.point().subtract(here).normalize()
              : new Vec3(0, 1, 0);
      var options =
          List.of(
              direction,
              new Vec3(0, 1, 0),
              new Vec3(1, .5, 0).normalize(),
              new Vec3(-1, .5, 0).normalize(),
              new Vec3(0, .5, 1).normalize(),
              new Vec3(0, .5, -1).normalize());
      for (var candidate : options)
        if (clear(core, candidate.scale(.65))) {
          velocity = candidate.scale(1.5 * (1 + .6 * BioshipLegEvolution.swimmingFraction(core)));
          break;
        }
    }
    if (level.getGameTime() % 20 == 0) {
      core.setChanged();
      level.sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
    }
  }

  /**
   * Habitat exposure includes partial body and actual extended-leg contact, independently of air
   * access.
   */
  static boolean touchesWater(LivingCoreBlockEntity core) {
    var transform = core.ship().logicalPose();
    for (var cell : BioshipDecay.blocks(core.ship()))
      for (double x : new double[] {-.49, 0, .49})
        for (double y : new double[] {-.49, 0, .49})
          for (double z : new double[] {-.49, 0, .49})
            if (waterAt(core, transform.transformPosition(Vec3.atCenterOf(cell).add(x, y, z))))
              return true;
    var legs = BioshipSupport.layout(core).legs();
    var origin = Vec3.atLowerCornerOf(core.structuralOrigin());
    long now = core.getLevel().getGameTime();
    for (int i = 0; i < legs.size(); i++) {
      var leg = legs.get(i);
      var root = transform.transformPosition(origin.add(leg.root()));
      var tip =
          core.aquatic().swimming()
              ? transform.transformPosition(
                  origin.add(leg.swimmingFoot(now * .45 + i * Math.PI / 2)))
              : core.crawl().legAnchors().foot(i, now);
      if (tip == null) continue;
      int steps = Math.max(1, (int) Math.ceil(root.distanceTo(tip) * 4));
      for (int step = 0; step <= steps; step++)
        if (waterAt(core, root.lerp(tip, step / (double) steps))) return true;
    }
    return false;
  }

  private static boolean waterAt(LivingCoreBlockEntity core, Vec3 point) {
    return core.getLevel().getFluidState(BlockPos.containing(point)).is(FluidTags.WATER)
        && !aboveFluid(core, point);
  }

  static boolean canBreathe(LivingCoreBlockEntity core) {
    return BioshipRespiration.canBreathe(core);
  }

  static boolean aboveFluid(LivingCoreBlockEntity core, Vec3 point) {
    var pos = BlockPos.containing(point);
    var fluid = core.getLevel().getFluidState(pos);
    return fluid.isEmpty() || point.y >= pos.getY() + fluid.getHeight(core.getLevel(), pos);
  }

  private static boolean waterWithinReach(LivingCoreBlockEntity core, Vec3 here) {
    for (int depth = 1; depth <= core.crawl().footingReach(); depth++)
      if (core.getLevel()
          .getFluidState(BlockPos.containing(here.add(0, -depth, 0)))
          .is(FluidTags.WATER)) return true;
    return false;
  }

  private boolean clear(LivingCoreBlockEntity core, Vec3 offset) {
    var ship = core.ship();
    for (var cell : BioshipDecay.blocks(ship)) {
      var point =
          BlockPos.containing(
              ship.logicalPose().transformPosition(Vec3.atCenterOf(cell)).add(offset));
      if (!core.getLevel().getBlockState(point).getCollisionShape(core.getLevel(), point).isEmpty())
        return false;
    }
    return true;
  }

  void propel(ServerSubLevel ship, RigidBodyHandle body, double dt) {
    var current = body.getLinearVelocity(new Vector3d());
    var goal = velocity;
    var acceleration =
        new Vector3d(goal.x - current.x, goal.y - current.y, goal.z - current.z).mul(4);
    acceleration.y -=
        dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(
                ship.getLevel())
            .y;
    body.addLinearAndAngularVelocity(acceleration.mul(dt), new Vector3d());
  }

  CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("Air", air);
    t.putInt("GillSamples", gillUsage.samples());
    t.putInt("GillUse", gillUsage.score());
    t.putDouble("GillUsePrecise", gillUsage.preciseScore());
    t.putBoolean("AirAccess", airAccess);
    t.putBoolean("SeekingAir", seekingAir);
    t.putInt("WaterUse", legUsage.water());
    t.putDouble("WaterUsePrecise", legUsage.preciseWater());
    t.putInt("UseSamples", legUsage.samples());
    t.putLong("LegEvolutionAfter", nextLegEvolution);
    t.putBoolean("Wet", wet);
    t.putBoolean("WaterContact", waterContact);
    t.putBoolean("Breathing", breathing);
    t.putBoolean("Swimming", swimming);
    return t;
  }

  void load(CompoundTag t) {
    gillUsage.restore(
        t.getInt("GillSamples"),
        t.contains("GillUsePrecise") ? t.getDouble("GillUsePrecise") : t.getDouble("GillUse"));
    airAccess = !t.contains("AirAccess") || t.getBoolean("AirAccess");
    seekingAir = t.getBoolean("SeekingAir");
    legUsage.restore(
        t.getInt("UseSamples"),
        t.contains("WaterUsePrecise") ? t.getDouble("WaterUsePrecise") : t.getDouble("WaterUse"));
    nextLegEvolution = t.getLong("LegEvolutionAfter");
    air = t.contains("Air") ? Math.clamp(t.getInt("Air"), 0, 300) : 300;
    wet = t.getBoolean("Wet");
    waterContact = t.getBoolean("WaterContact");
    breathing = !t.contains("Breathing") || t.getBoolean("Breathing");
    swimming = t.getBoolean("Swimming");
  }
}
