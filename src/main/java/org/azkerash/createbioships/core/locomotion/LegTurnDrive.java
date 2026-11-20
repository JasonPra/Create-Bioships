package org.azkerash.createbioships.core.locomotion;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Balanced foot impulses turn a hull without assigning its orientation or angular velocity. */
public final class LegTurnDrive {
  public record Foot(Vec3 point, UUID support) {}

  public static final double MAX_YAW_RATE = .4;
  private static final double MAX_ANGULAR_ACCELERATION = .6, RESPONSE = 3;
  private static final double GROUND_ACCELERATION = 9.81, TRACTION = .75;
  private static final double MIN_LEVER_SQUARED = 1e-6;

  private LegTurnDrive() {}

  public static List<Vec3> impulses(
      List<Foot> feet,
      Vec3 normal,
      double mass,
      double inertia,
      double currentYaw,
      double requestedYaw,
      double dt) {
    var zero = Collections.nCopies(feet.size(), Vec3.ZERO);
    if (feet.size() < 2 || !(mass > 0 && inertia > 0 && dt > 0 && dt <= .1)) {
      return zero;
    }
    normal = normal.normalize();
    var center = Vec3.ZERO;
    for (var foot : feet) {
      center = center.add(foot.point);
    }
    center = center.scale(1d / feet.size());
    var levers = new ArrayList<Vec3>();
    double leverage = 0;
    for (var foot : feet) {
      var offset = foot.point.subtract(center);
      offset = offset.subtract(normal.scale(offset.dot(normal)));
      levers.add(offset);
      leverage += offset.lengthSqr();
    }
    if (leverage < MIN_LEVER_SQUARED) {
      return zero;
    }
    double target = Math.clamp(requestedYaw, -MAX_YAW_RATE, MAX_YAW_RATE);
    double acceleration =
        Math.clamp(
            (target - currentYaw) * RESPONSE, -MAX_ANGULAR_ACCELERATION, MAX_ANGULAR_ACCELERATION);
    double factor = inertia * acceleration * dt / leverage;
    double maximum = mass * GROUND_ACCELERATION * TRACTION * dt / feet.size();
    double largest = levers.stream().mapToDouble(Vec3::length).max().orElse(0) * Math.abs(factor);
    if (largest > maximum) {
      factor *= maximum / largest;
    }
    var result = new ArrayList<Vec3>();
    for (var lever : levers) {
      result.add(normal.cross(lever).scale(factor));
    }
    // The levers are centered on the support polygon: sum(F)=0 while sum(r x F) supplies yaw.
    return List.copyOf(result);
  }
}
