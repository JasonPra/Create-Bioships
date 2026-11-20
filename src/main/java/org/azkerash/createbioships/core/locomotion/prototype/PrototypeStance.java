package org.azkerash.createbioships.core.locomotion.prototype;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Friction-foot stance policy. Anchored/climbing appendages require a different contact controller.
 */
public final class PrototypeStance {
  public record Surface(boolean valid, Vec3 normal, double grade) {}

  public static final Surface LEVEL = new Surface(false, new Vec3(0, 1, 0), 0);
  private static final double MAX_BODY_TILT = Math.toRadians(25);
  private static final double FULL_ADAPTATION_GRADE = .5;
  private static final double FRONT_NARROWING = .3, REAR_WIDENING = .15;
  private static final double DOWNHILL_TUCK = .18, UPHILL_REACH = .12, REAR_LEVERAGE = .15;
  public static final double ADAPTATION_GRADE = .08;

  private PrototypeStance() {}

  public static Surface fit(List<Vec3> contacts, Vec3 forward) {
    if (contacts.size() < 3) {
      return LEVEL;
    }
    var center = Vec3.ZERO;
    for (var point : contacts) {
      center = center.add(point);
    }
    center = center.scale(1d / contacts.size());
    double xx = 0, zz = 0, xz = 0, xy = 0, zy = 0;
    for (var point : contacts) {
      var d = point.subtract(center);
      xx += d.x * d.x;
      zz += d.z * d.z;
      xz += d.x * d.z;
      xy += d.x * d.y;
      zy += d.z * d.y;
    }
    double determinant = xx * zz - xz * xz;
    if (determinant < 1e-8) {
      return LEVEL;
    }
    double x = (xy * zz - zy * xz) / determinant;
    double z = (zy * xx - xy * xz) / determinant;
    return new Surface(true, new Vec3(-x, 1, -z).normalize(), x * forward.x + z * forward.z);
  }

  public static boolean adapts(PrototypeGait.Leg leg) {
    return leg.profile().contact() == LegMotionProfile.GroundContact.FRICTION
        && (leg.profile().rearLeverage() || leg.profile().downhillTuck());
  }

  public static Vec3 foot(
      PrototypeGait.Leg leg, Vec3 original, Vec3 center, Vec3 forward, double grade) {
    if (!adapts(leg) || Math.abs(grade) < ADAPTATION_GRADE) {
      return original;
    }
    var right = new Vec3(forward.z, 0, -forward.x).normalize();
    boolean front = leg.hip().subtract(center).dot(forward) >= 0;
    double severity = Math.clamp(Math.abs(grade) / FULL_ADAPTATION_GRADE, 0, 1);
    if (grade < 0 && leg.profile().downhillTuck()) {
      double lateral = original.subtract(center).dot(right);
      if (front) {
        return original
            .subtract(right.scale(lateral * FRONT_NARROWING * severity))
            .subtract(forward.scale(leg.height() * DOWNHILL_TUCK * severity));
      }
      return original.add(right.scale(lateral * REAR_WIDENING * severity));
    }
    if (grade > 0 && leg.profile().rearLeverage()) {
      return original.add(
          forward.scale(leg.height() * severity * (front ? UPHILL_REACH : -REAR_LEVERAGE)));
    }
    return original;
  }

  public static Vec3 bodyUp(Surface surface) {
    // Crab legs do not attach to a descent. Keep the hull level and use leg extension instead.
    if (!surface.valid || surface.grade <= 0) {
      return new Vec3(0, 1, 0);
    }
    var horizontal = new Vec3(surface.normal.x, 0, surface.normal.z);
    double tilt = Math.min(MAX_BODY_TILT, Math.atan2(horizontal.length(), surface.normal.y));
    return horizontal.normalize().scale(Math.sin(tilt)).add(0, Math.cos(tilt), 0);
  }
}
