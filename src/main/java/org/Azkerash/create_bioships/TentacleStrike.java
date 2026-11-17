package org.Azkerash.create_bioships;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A telegraphed, world-locked swipe. It never changes its victim's velocity or position. */
public final class TentacleStrike {
  public static final int WINDUP = 12, SWEEP = 6;
  public static final double TIP_HALF_SIZE = .2;
  private Vec3 aim;
  private UUID victim;
  private long started;
  private boolean hit;
  private Vec3 previousTip;
  private long previousTick = Long.MIN_VALUE;

  public Vec3 collisionTip() {
    return previousTip;
  }

  public boolean active() {
    return aim != null;
  }

  public LivingEntity victim(ServerLevel level) {
    return victim != null && level.getEntity(victim) instanceof LivingEntity e ? e : null;
  }

  public void begin(LivingEntity target, long now) {
    aim = target.getBoundingBox().getCenter();
    victim = target.getUUID();
    started = now;
    hit = false;
    previousTip = null;
    previousTick = Long.MIN_VALUE;
  }

  public void clear() {
    aim = null;
    victim = null;
    hit = false;
    previousTip = null;
    previousTick = Long.MIN_VALUE;
  }

  public boolean finished(long now) {
    return active() && now - started >= WINDUP + SWEEP;
  }

  public Vec3 tip(Vec3 root, double now, double reach) {
    if (aim == null) return root;
    var delta = aim.subtract(root);
    if (delta.length() > reach) delta = delta.normalize().scale(reach);
    double age = now - started;
    if (age < WINDUP)
      return root.add(delta.scale(.2)).add(delta.normalize().cross(new Vec3(0, 1, 0)).scale(.6));
    double t = Math.clamp((age - WINDUP) / SWEEP, 0, 1);
    return root.add(delta.yRot((float) (.65 - 1.3 * t)));
  }

  public boolean sweeping(long now) {
    return active() && now - started >= WINDUP && now - started <= WINDUP + SWEEP && !hit;
  }

  public boolean tick(
      LivingCoreBlockEntity core, Vec3 root, LivingEntity target, double reach, float damage) {
    long now = core.getLevel().getGameTime();
    if (!active()) return false;
    var end = tip(root, now, reach);
    var previous =
        previousTip != null && previousTick == now - 1
            ? previousTip
            : tip(root, Math.max(started + WINDUP, now - 1), reach);
    previousTip = end;
    previousTick = now;
    if (!sweeping(now) || target == null || !target.isAlive()) return false;
    if (tipCollision(target.getBoundingBox(), previous, end)
        && BioshipReach.clear(core, target.getBoundingBox().getCenter(), false, reach)) {
      hit = true;
      var attacker = BioshipTargetEntity.attackSource(core, target);
      var motion = core.ship().latestLinearVelocity;
      var direction = target.getBoundingBox().getCenter().subtract(previous).normalize();
      double closing = motion.x * direction.x + motion.y * direction.y + motion.z * direction.z;
      float impact = ImpactDamage.resolve(damage, closing);
      boolean damaged = target.hurt(core.getLevel().damageSources().mobAttack(attacker), impact);
      if (damaged) FeedingMouthBlockEntity.bite(core, target.getBoundingBox().getCenter());
      return damaged;
    }
    return false;
  }

  /** Sweeps a 0.4-block tip box, never the shaft, to prevent tunneling between ticks. */
  static boolean tipCollision(AABB target, Vec3 previous, Vec3 current) {
    var expanded = target.inflate(TIP_HALF_SIZE);
    return expanded.contains(previous)
        || expanded.contains(current)
        || expanded.clip(previous, current).isPresent();
  }

  public CompoundTag save() {
    var tag = new CompoundTag();
    if (aim != null) tag.put("Aim", VisionDebug.vector(aim));
    if (victim != null) tag.putUUID("Victim", victim);
    tag.putLong("Started", started);
    tag.putBoolean("Hit", hit);
    if (previousTip != null) tag.put("Tip", VisionDebug.vector(previousTip));
    tag.putLong("TipAt", previousTick);
    return tag;
  }

  public void load(CompoundTag tag) {
    aim = tag.contains("Aim") ? VisionDebug.vector(tag.getCompound("Aim")) : null;
    victim = tag.hasUUID("Victim") ? tag.getUUID("Victim") : null;
    started = tag.getLong("Started");
    hit = tag.getBoolean("Hit");
    previousTip = tag.contains("Tip") ? VisionDebug.vector(tag.getCompound("Tip")) : null;
    previousTick = tag.getLong("TipAt");
  }
}
