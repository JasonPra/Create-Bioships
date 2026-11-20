package org.azkerash.createbioships.core.locomotion.prototype;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Three rigid segments: a fixed coxa followed by an analytic two-link knee/ankle solve. */
public final class PrototypeLegKinematics {
  private static final double REACH_EPSILON = 1e-5;
  private static final double DEGENERATE_LENGTH_SQUARED = 1e-10;

  private PrototypeLegKinematics() {}

  public static List<Vec3> solve(
      Vec3 hip, Vec3 outward, Vec3 target, double coxa, double thigh, double shin) {
    if (!(coxa > 0 && thigh > 0 && shin > 0) || !Double.isFinite(coxa + thigh + shin)) {
      throw new IllegalArgumentException("Segment lengths must be finite and positive");
    }
    var side =
        outward.lengthSqr() > DEGENERATE_LENGTH_SQUARED ? outward.normalize() : new Vec3(1, 0, 0);
    var socket = hip.add(side.scale(coxa));
    var delta = target.subtract(socket);
    var along =
        delta.lengthSqr() > DEGENERATE_LENGTH_SQUARED ? delta.normalize() : new Vec3(0, -1, 0);
    double distance =
        Math.clamp(
            delta.length(), Math.abs(thigh - shin) + REACH_EPSILON, thigh + shin - REACH_EPSILON);
    double projection = (thigh * thigh - shin * shin + distance * distance) / (2 * distance);
    double rise = Math.sqrt(Math.max(0, thigh * thigh - projection * projection));
    // Choose the knee above the hip-to-foot axis; this keeps the existing insect-like silhouette.
    var up = new Vec3(0, 1, 0);
    var bend = up.subtract(along.scale(up.dot(along)));
    if (bend.lengthSqr() < DEGENERATE_LENGTH_SQUARED) {
      bend = side.subtract(along.scale(side.dot(along)));
    }
    if (bend.lengthSqr() < DEGENERATE_LENGTH_SQUARED) {
      var fallback = Math.abs(along.x) < .9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
      bend = fallback.subtract(along.scale(fallback.dot(along)));
    }
    var knee = socket.add(along.scale(projection)).add(bend.normalize().scale(rise));
    return List.of(hip, socket, knee, socket.add(along.scale(distance)));
  }
}
