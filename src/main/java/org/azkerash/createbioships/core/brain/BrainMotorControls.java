package org.azkerash.createbioships.core.brain;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.*;

/** Brain control surface. Behavior cards can request motion without knowing joints or gait. */
public final class BrainMotorControls {
  private static final double MAX_COMMAND_SPEED = 2;
  private static final double MAX_YAW_COMMAND = 1;
  private static final int MAX_LEASE_TICKS = 200;

  public record Request(
      Vec3 velocity,
      double yawRate,
      long expires,
      org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode mode) {
    public Request(Vec3 velocity, double yawRate, long expires) {
      this(
          velocity,
          yawRate,
          expires,
          org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND);
    }
  }

  private static final Map<ServerSubLevel, Request> REQUESTS = new WeakHashMap<>();

  private static final int REJECTION_LIFETIME_TICKS = 40;

  public record Rejection(Vec3 movement, long tick) {}

  private static final Map<ServerSubLevel, Rejection> REJECTIONS = new WeakHashMap<>();

  public static Rejection lastRejection(ServerSubLevel body) {
    var rejection = REJECTIONS.get(SubLevelCreatureParts.root(body));
    return rejection != null
            && body.getLevel().getGameTime() - rejection.tick() <= REJECTION_LIFETIME_TICKS
        ? rejection
        : null;
  }

  /** Cancel the unsafe lease without locking out a later collision-clear escape request. */
  public static void obstructed(ServerSubLevel body) {
    var root = SubLevelCreatureParts.root(body);
    var request = REQUESTS.get(root);
    if (request != null) {
      REJECTIONS.put(root, new Rejection(request.velocity(), root.getLevel().getGameTime()));
    }
    stop(body);
  }

  private BrainMotorControls() {}

  public static boolean move(ServerSubLevel body, Vec3 velocity, double yawRate, int leaseTicks) {
    return move(
        body,
        velocity,
        yawRate,
        leaseTicks,
        org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND);
  }

  public static boolean move(
      ServerSubLevel body,
      Vec3 velocity,
      double yawRate,
      int leaseTicks,
      org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode mode) {
    body = SubLevelCreatureParts.root(body);
    if (!active(body)
        || !org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.allowed(body, mode)
        || !Double.isFinite(velocity.x)
        || !Double.isFinite(velocity.y)
        || !Double.isFinite(velocity.z)
        || !Double.isFinite(yawRate)) {
      return false;
    }
    var movement =
        mode == org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.WATER
            ? velocity
            : new Vec3(velocity.x, 0, velocity.z);
    if (mode == org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND) {
      org.azkerash.createbioships.core.legs.SubLevelLegs.climbIntent(body, velocity);
      movement = org.azkerash.createbioships.core.legs.SubLevelLegs.surfaceMotion(body, velocity);
    }
    double limit =
        mode == org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.WATER
                || org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion.get(
                        body)
                    == org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion
                        .Mode.NATIVE
            ? MAX_COMMAND_SPEED
            : org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion
                .MAX_SPEED_BLOCKS_PER_SECOND;
    if (movement.lengthSqr() > limit * limit) {
      movement = movement.normalize().scale(limit);
    }
    if (!org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.movementClear(
        body, movement, yawRate)) {
      REJECTIONS.put(body, new Rejection(movement, body.getLevel().getGameTime()));
      stop(body);
      return false;
    }
    if (movement.lengthSqr() > .001) {
      REJECTIONS.remove(body);
    }
    REQUESTS.put(
        body,
        new Request(
            movement,
            Math.clamp(yawRate, -MAX_YAW_COMMAND, MAX_YAW_COMMAND),
            body.getLevel().getGameTime() + Math.clamp(leaseTicks, 1, MAX_LEASE_TICKS),
            mode));
    return true;
  }

  public static boolean active(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) {
      return false;
    }
    body = SubLevelCreatureParts.root(body);
    return !body.isRemoved()
        && SubLevelData.isLiving(body)
        && !SubLevelHealth.status(body).dead()
        && SubLevelBrains.get(body).capacity() > 0;
  }

  /** Standing is an idle brain reflex, independent of a behavior card or movement lease. */
  public static boolean groundPostureEnabled(ServerSubLevel body) {
    if (org.azkerash.createbioships.core.flippers.SubLevelFlippers.supporting(body)) {
      return false;
    }
    if (!active(body)
        || !org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.allowed(
            body, org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND)) {
      return false;
    }
    return current(body).mode()
        == org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.GROUND;
  }

  public static Request current(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var request = REQUESTS.get(body);
    if (!active(body)
        || request == null
        || body.getLevel().getGameTime() >= request.expires
        || !org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.allowed(
            body, request.mode)) {
      REQUESTS.remove(body);
      return new Request(Vec3.ZERO, 0, 0);
    }
    return request;
  }

  public static void stop(ServerSubLevel body) {
    REQUESTS.remove(SubLevelCreatureParts.root(body));
  }
}
