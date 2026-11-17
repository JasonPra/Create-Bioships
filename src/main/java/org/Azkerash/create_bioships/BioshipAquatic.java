package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.navigation.SwimVelocityControl;
import org.Azkerash.create_bioships.creature.respiration.AirSupply;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.joml.Vector3d;

/** Water propulsion is separate from flight; breathing remains a finite survival resource. */
public final class BioshipAquatic {
  private final WaterNavigation navigation = new WaterNavigation();
  private final SwimVelocityControl steering = new SwimVelocityControl();

  public java.util.List<Vec3> path() {
    return navigation.remaining();
  }

  public CompoundTag navigationDebug(long now) {
    var tag = navigation.debug(now);
    tag.put("Velocity", org.Azkerash.create_bioships.network.VisionDebug.vector(velocity));
    tag.putDouble("Speed", velocity.length());
    tag.putInt("Air", air.remaining());
    tag.putBoolean("SeekingAir", air.seeking());
    return tag;
  }

  private final AirSupply air = new AirSupply();
  private long lastTick = Long.MIN_VALUE;
  private volatile boolean wet, swimming;
  private boolean waterContact;
  private boolean breathing = true, airAccess = true;
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
    return air.remaining();
  }

  boolean needsAir() {
    return air.needsAir(breathing, airAccess);
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
    air.tick(breathing, airAccess, BioshipRespiration.activeGills(core));
    if (!breathing && air.remaining() == 0 && level.getGameTime() % 20 == 0) {
      if (airAccess) core.body().asphyxiate(core);
      else core.body().drown(core);
    }
    swimming =
        (wet || air.seeking() && breathing && waterWithinReach(core, here))
            && !core.body().dead()
            && (core.canFly() || core.crawl().carriedLoad(core) <= core.crawl().loadCapacity());
    velocity = Vec3.ZERO;
    if (swimming) {
      if (air.seeking() && breathing) {
        // Stay braced at the waterline until air is replenished instead of diving after one breath.
        if (level.getGameTime() % 20 == 0) core.setChanged();
        return;
      }
      var selected = core.brain().lastDecision();
      if (needsAir()) velocity = navigation.towardAir(core);
      else if (selected != null && selected.point() != null)
        velocity = navigation.toward(core, selected.point());
      else navigation.clear();
      velocity = velocity.scale(1.5 * (1 + .6 * BioshipLegEvolution.swimmingFraction(core)));
    }
    if (!swimming) navigation.clear();
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

  void propel(ServerSubLevel ship, RigidBodyHandle body, double dt) {
    var current = body.getLinearVelocity(new Vector3d());
    var goal = velocity;
    var acceleration =
        steering.acceleration(
            new SwimVelocityControl.Vector(goal.x, goal.y, goal.z),
            new SwimVelocityControl.Vector(current.x, current.y, current.z),
            dt);
    body.addLinearAndAngularVelocity(
        new Vector3d(acceleration.x(), acceleration.y(), acceleration.z()).mul(dt), new Vector3d());
  }

  public CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("Air", air.remaining());
    t.putInt("GillSamples", gillUsage.samples());
    t.putInt("GillUse", gillUsage.score());
    t.putDouble("GillUsePrecise", gillUsage.preciseScore());
    t.putBoolean("AirAccess", airAccess);
    t.putBoolean("SeekingAir", air.seeking());
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

    legUsage.restore(
        t.getInt("UseSamples"),
        t.contains("WaterUsePrecise") ? t.getDouble("WaterUsePrecise") : t.getDouble("WaterUse"));
    nextLegEvolution = t.getLong("LegEvolutionAfter");
    air.restore(t.contains("Air") ? t.getInt("Air") : 300, t.getBoolean("SeekingAir"));
    wet = t.getBoolean("Wet");
    waterContact = t.getBoolean("WaterContact");
    breathing = !t.contains("Breathing") || t.getBoolean("Breathing");
    swimming = t.getBoolean("Swimming");
  }
}
