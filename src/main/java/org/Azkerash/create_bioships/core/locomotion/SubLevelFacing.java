package org.Azkerash.create_bioships.core.locomotion;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.brain.behavior.BrainBehaviors;
import org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths;

/**
 * A creature's anatomical front, independent of Sable's coordinate axes or its current velocity.
 */
public final class SubLevelFacing {
  private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 1e-8;
  private static final double MAX_DRIVE_HEADING_ERROR_RADIANS = Math.PI / 3;
  private static final double YAW_CORRECTION_GAIN = 2;
  private static final double MAX_STEERING_YAW_RATE = .8;

  public static final String KEY = "front";

  private SubLevelFacing() {}

  public static Direction local(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) {
      return Direction.SOUTH;
    }
    body = SubLevelCreatureParts.root(body);
    SubLevelData.initialize(body);
    var root = body.getUserDataTag();
    var front = Direction.byName(root.getCompound(SubLevelData.STATE).getString(KEY));
    if (front != null && front.getAxis() != Direction.Axis.Y) {
      return front;
    }
    // Preserve the +Z convention used by existing Wander behaviors.
    write(body, Direction.SOUTH);
    return Direction.SOUTH;
  }

  private static void write(ServerSubLevel body, Direction front) {
    var root = body.getUserDataTag().copy();
    root.getCompound(SubLevelData.STATE).putString(KEY, front.getName());
    body.setUserDataTag(root);
  }

  public static boolean set(ServerSubLevel body, Direction front) {
    if (front == null
        || front.getAxis() == Direction.Axis.Y
        || !SubLevelCreatureParts.available(body)) {
      return false;
    }
    body = SubLevelCreatureParts.root(body);
    if (!SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) {
      return false;
    }
    if (local(body) == front) {
      return true;
    }
    write(body, front);
    BrainBehaviors.discard(body);
    BrainMotorControls.stop(body);
    SubLevelPaths.clear(body);
    return true;
  }

  public static Vec3 world(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var front = local(body);
    var v =
        body.logicalPose()
            .orientation()
            .transform(new org.joml.Vector3d(front.getStepX(), 0, front.getStepZ()));
    return new Vec3(v.x, v.y, v.z).normalize();
  }

  public record Steering(Vec3 velocity, double yawRate, double error) {}

  /** Project heading onto the ground plane; turn before driving away sideways or backwards. */
  public static Steering towards(Vec3 forward, Vec3 delta, double speed, boolean swimming) {
    var horizontal = new Vec3(delta.x, 0, delta.z);
    double error = 0;
    if (horizontal.lengthSqr() > MIN_HORIZONTAL_LENGTH_SQUARED) {
      if (forward.x * forward.x + forward.z * forward.z < MIN_HORIZONTAL_LENGTH_SQUARED) {
        return new Steering(Vec3.ZERO, 0, Math.PI);
      }
      error = Math.atan2(delta.x, delta.z) - Math.atan2(forward.x, forward.z);
      error = Math.atan2(Math.sin(error), Math.cos(error));
    }
    double alignment = Math.max(0, Math.cos(error));
    if (Math.abs(error) > MAX_DRIVE_HEADING_ERROR_RADIANS) {
      alignment = 0;
    }
    var direction = (swimming ? delta : horizontal).normalize();
    return new Steering(
        direction.scale(speed * alignment * alignment),
        Math.clamp(error * YAW_CORRECTION_GAIN, -MAX_STEERING_YAW_RATE, MAX_STEERING_YAW_RATE),
        error);
  }
}
