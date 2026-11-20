package org.azkerash.createbioships.core.locomotion.prototype;

import net.minecraft.world.phys.Vec3;

/** Bounded adhesive support reaction. No reachable toes means no levitation or wall grip. */
public final class ArachnidBodyMotor {
  public static final int MIN_ANCHORS = 3;
  private static final double GRAVITY = 9.81;
  private static final double TANGENT_ACCELERATION = 12;
  private static final double NORMAL_ACCELERATION = 32;
  private static final double HEIGHT_GAIN = 6, NORMAL_RESPONSE = 12, MAX_NORMAL_SPEED = 3;
  private static final double VERTICAL_COMMAND_EPSILON = .001;
  private static final double TURN_GAIN = 6, TURN_RESPONSE = 8, MAX_YAW_RATE = 1.5;

  private ArachnidBodyMotor() {}

  public static Vec3 tangent(Vec3 value, Vec3 normal) {
    return value.subtract(normal.scale(value.dot(normal)));
  }

  /** Preserve explicit surface-tangent commands; transport horizontal headings onto the surface. */
  public static Vec3 surfaceMotion(Vec3 requested, Vec3 normal) {
    if (Math.abs(requested.y) > VERTICAL_COMMAND_EPSILON) {
      return tangent(requested, normal);
    }
    var turn =
        new org.joml.Quaterniond()
            .rotationTo(
                new org.joml.Vector3d(0, 1, 0),
                new org.joml.Vector3d(normal.x, normal.y, normal.z));
    var result = turn.transform(new org.joml.Vector3d(requested.x, requested.y, requested.z));
    return new Vec3(result.x, result.y, result.z);
  }

  public static PrototypeBodyMotor.Delta calculate(
      Vec3 velocity,
      Vec3 spin,
      Vec3 up,
      Vec3 normal,
      Vec3 requested,
      Vec3 supportVelocity,
      double yawRate,
      double heightError,
      int anchors,
      double dt) {
    if (anchors < MIN_ANCHORS || !Double.isFinite(heightError) || !(dt > 0 && dt <= .1)) {
      return new PrototypeBodyMotor.Delta(Vec3.ZERO, Vec3.ZERO);
    }
    normal = normal.normalize();
    var relative = velocity.subtract(supportVelocity);
    var drive = tangent(requested.subtract(relative), normal);
    drive = limit(drive, TANGENT_ACCELERATION * dt);
    double target = Math.clamp(heightError * HEIGHT_GAIN, -MAX_NORMAL_SPEED, MAX_NORMAL_SPEED);
    double pull =
        Math.clamp(
            (target - relative.dot(normal)) * NORMAL_RESPONSE,
            -NORMAL_ACCELERATION,
            NORMAL_ACCELERATION);
    // Cancel gravity only while supported, including on vertical and inverted surfaces.
    var correction = drive.add(normal.scale(pull * dt)).add(0, GRAVITY * dt, 0);
    var targetSpin =
        up.cross(normal)
            .scale(TURN_GAIN)
            .add(normal.scale(Math.clamp(yawRate, -MAX_YAW_RATE, MAX_YAW_RATE)));
    return new PrototypeBodyMotor.Delta(
        correction, targetSpin.subtract(spin).scale(Math.min(1, TURN_RESPONSE * dt)));
  }

  private static Vec3 limit(Vec3 value, double maximum) {
    return value.lengthSqr() > maximum * maximum ? value.normalize().scale(maximum) : value;
  }
}
