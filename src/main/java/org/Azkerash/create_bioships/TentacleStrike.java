package org.Azkerash.create_bioships;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A telegraphed, world-locked swipe. It never changes its victim's velocity or position. */
public final class TentacleStrike {
  public static final int WINDUP = 12, SWEEP = 6;
  private Vec3 aim;
  private UUID victim;
  private long started;
  private boolean hit;

  public boolean active() {
    return aim != null;
  }

  public LivingEntity victim(ServerLevel level) {
    return victim != null && level.getEntity(victim) instanceof LivingEntity e ? e : null;
  }

  void begin(LivingEntity target, long now) {
    aim = target.getBoundingBox().getCenter();
    victim = target.getUUID();
    started = now;
    hit = false;
  }

  void clear() {
    aim = null;
    victim = null;
    hit = false;
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

  boolean tick(
      LivingCoreBlockEntity core, Vec3 root, LivingEntity target, double reach, float damage) {
    long now = core.getLevel().getGameTime();
    if (!active()
        || now - started > WINDUP + SWEEP
        || now - started < WINDUP
        || hit
        || target == null
        || !target.isAlive()) return false;
    var box = target.getBoundingBox().inflate(.3);
    var end = tip(root, now, reach);
    var previous = tip(root, now - 1, reach);
    if ((box.contains(end)
            || box.clip(root, end).isPresent()
            || box.clip(previous, end).isPresent())
        && root.distanceToSqr(target.getBoundingBox().getCenter()) <= Math.pow(reach + .6, 2)
        && BioshipReach.clear(core, target.getBoundingBox().getCenter(), false, reach)) {
      hit = true;
      var attacker = BioshipTargetEntity.attackSource(core, target);
      return target.hurt(core.getLevel().damageSources().mobAttack(attacker), damage);
    }
    return false;
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    if (aim != null) tag.put("Aim", VisionDebug.vector(aim));
    if (victim != null) tag.putUUID("Victim", victim);
    tag.putLong("Started", started);
    tag.putBoolean("Hit", hit);
    return tag;
  }

  void load(CompoundTag tag) {
    aim = tag.contains("Aim") ? VisionDebug.vector(tag.getCompound("Aim")) : null;
    victim = tag.hasUUID("Victim") ? tag.getUUID("Victim") : null;
    started = tag.getLong("Started");
    hit = tag.getBoolean("Hit");
  }
}
