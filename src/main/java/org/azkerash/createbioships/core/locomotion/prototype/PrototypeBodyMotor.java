package org.azkerash.createbioships.core.locomotion.prototype;

import net.minecraft.world.phys.Vec3;

/** Velocity motor for the prototype hull. Collision response and integration remain with Sable. */
public final class PrototypeBodyMotor {
  public record Delta(Vec3 velocity, Vec3 angularVelocity) {}

  private static final double GRAVITY = 9.81;
  private static final double ACCELERATION = 12;
  private static final double HEIGHT_GAIN = 6;
  private static final double HEIGHT_RESPONSE = 12;
  private static final double MAX_VERTICAL_SPEED = 4;
  private static final double UPRIGHT_GAIN = 6;
  private static final double ANGULAR_RESPONSE = 8;

  private PrototypeBodyMotor() {}

  public static Delta calculate(
      Vec3 velocity,
      Vec3 spin,
      Vec3 up,
      Vec3 requested,
      double yawRate,
      double heightError,
      double dt,
      boolean supported) {
    if (!supported || !Double.isFinite(heightError) || !(dt > 0 && dt <= .1)) {
      return new Delta(Vec3.ZERO, Vec3.ZERO);
    }
    var horizontal = new Vec3(requested.x - velocity.x, 0, requested.z - velocity.z);
    double maximum = ACCELERATION * dt;
    if (horizontal.lengthSqr() > maximum * maximum) {
      horizontal = horizontal.normalize().scale(maximum);
    }
    double targetY = Math.clamp(heightError * HEIGHT_GAIN, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
    double vertical = (targetY - velocity.y) * Math.min(1, HEIGHT_RESPONSE * dt) + GRAVITY * dt;
    var upright = up.cross(new Vec3(0, 1, 0)).scale(UPRIGHT_GAIN);
    var targetSpin = new Vec3(upright.x, Math.clamp(yawRate, -1.5, 1.5), upright.z);
    var turn = targetSpin.subtract(spin).scale(Math.min(1, ANGULAR_RESPONSE * dt));
    return new Delta(new Vec3(horizontal.x, vertical, horizontal.z), turn);
  }
}
