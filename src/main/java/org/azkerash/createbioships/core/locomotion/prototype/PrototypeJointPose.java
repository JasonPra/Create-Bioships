package org.azkerash.createbioships.core.locomotion.prototype;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Projects analytic limb directions into saved joint windows, preserving every segment length. */
public final class PrototypeJointPose {
  /** Half swivel range and bend excursion around the physical rest pose, in radians. */
  public record Limit(double swivel, double bend) {
    public Limit {
      if (!Double.isFinite(swivel + bend)) {
        throw new IllegalArgumentException("Joint ranges must be finite");
      }
      swivel = Math.clamp(swivel, 0, Math.PI);
      bend = Math.clamp(bend, 0, Math.PI);
    }
  }

  private static final double MIN_DIRECTION_LENGTH_SQUARED = 1e-10;

  private PrototypeJointPose() {}

  private static final double REACH_TOLERANCE_SQUARED = .0004;
  private static final double WORKING_RANGE_STEP = Math.toRadians(20);

  public static List<Vec3> solve(
      PrototypeGait.Leg leg,
      Vec3 target,
      List<Vec3> restDirections,
      Vec3 rootUp,
      List<Limit> limits) {
    var up = rootUp.normalize();
    var rest = restDirections.getFirst().normalize();
    var side = rest.subtract(up.scale(rest.dot(up))).normalize();
    var tangent = up.cross(side).normalize();
    var delta = target.subtract(leg.hip());
    double yaw = Math.atan2(delta.dot(tangent), delta.dot(side));
    yaw = Math.clamp(yaw, -limits.getFirst().swivel, limits.getFirst().swivel);
    var aimed = side.scale(Math.cos(yaw)).add(tangent.scale(Math.sin(yaw)));
    double resting = Math.asin(Math.clamp(rest.dot(up), -1, 1));
    List<Vec3> best = null;
    double error = Double.POSITIVE_INFINITY;
    int expansions = leg.profile().adaptiveReach() ? 2 : 0;
    for (int step : new int[] {0, 1, -1, 2, -2}) {
      if (Math.abs(step) > expansions) {
        continue;
      }
      double pitch =
          resting
              + Math.clamp(
                  step * WORKING_RANGE_STEP, -limits.getFirst().bend, limits.getFirst().bend);
      var coxa = aimed.scale(Math.cos(pitch)).add(up.scale(Math.sin(pitch)));
      var analytic =
          PrototypeLegKinematics.solve(
              leg.hip(),
              coxa,
              target,
              leg.coxa(),
              leg.thigh(),
              leg.shin(),
              PrototypeGait.anchored(leg) ? rootUp : new Vec3(0, 1, 0));
      var bounded = constrain(analytic, restDirections, rootUp, limits);
      double distance = bounded.getLast().distanceToSqr(target);
      if (distance < error) {
        best = bounded;
        error = distance;
      }
      if (error <= REACH_TOLERANCE_SQUARED) {
        break;
      }
    }
    return best;
  }

  public static List<Vec3> constrain(
      List<Vec3> desired, List<Vec3> restDirections, Vec3 rootUp, List<Limit> limits) {
    if (desired.size() != 4 || restDirections.size() != 3 || limits.size() != 3) {
      throw new IllegalArgumentException("Compact limbs require three joint definitions");
    }
    var points = new ArrayList<Vec3>();
    points.add(desired.getFirst());
    var parent = rootUp.normalize();
    for (int i = 0; i < 3; i++) {
      var restParent = i == 0 ? rootUp.normalize() : restDirections.get(i - 1).normalize();
      var restChild = restDirections.get(i).normalize();
      var move = new Quaterniond().rotationTo(vector(restParent), vector(parent));
      var alignedRest = move.transform(vector(restChild));
      var aligned = new Vec3(alignedRest.x, alignedRest.y, alignedRest.z);
      var reference = aligned.subtract(parent.scale(aligned.dot(parent)));
      if (reference.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED) {
        var fallback = Math.abs(parent.x) < .9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        reference = fallback.subtract(parent.scale(fallback.dot(parent)));
      }
      reference = reference.normalize();
      var tangent = parent.cross(reference).normalize();
      var delta = desired.get(i + 1).subtract(desired.get(i));
      double length = delta.length();
      var direction = delta.normalize();
      double resting = Math.acos(Math.clamp(restParent.dot(restChild), -1, 1));
      double polar = Math.acos(Math.clamp(parent.dot(direction), -1, 1));
      var projected = direction.subtract(parent.scale(direction.dot(parent)));
      double swivel =
          projected.lengthSqr() < MIN_DIRECTION_LENGTH_SQUARED
              ? 0
              : Math.atan2(projected.dot(tangent), projected.dot(reference));
      var limit = limits.get(i);
      polar =
          Math.clamp(
              polar, Math.max(0, resting - limit.bend), Math.min(Math.PI, resting + limit.bend));
      swivel = Math.clamp(swivel, -limit.swivel, limit.swivel);
      var radial = reference.scale(Math.cos(swivel)).add(tangent.scale(Math.sin(swivel)));
      parent = parent.scale(Math.cos(polar)).add(radial.scale(Math.sin(polar))).normalize();
      points.add(points.getLast().add(parent.scale(length)));
    }
    return List.copyOf(points);
  }

  private static Vector3d vector(Vec3 value) {
    return new Vector3d(value.x, value.y, value.z);
  }
}
