package org.azkerash.createbioships.core.flippers;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Bounded forward thrust and yaw torque allocation in one shared world coordinate frame. */
public final class FlipperThrust {
  public record Fin(BlockPos socket, Vec3 offset, double capacity) {}

  private static final double TURN_THRESHOLD = .02;
  private static final double REGULARIZATION = 1e-9;

  private FlipperThrust() {}

  public static Map<BlockPos, Double> allocate(
      List<Fin> available, Vec3 forward, double force, double torque, double turnDirection) {
    if (!Double.isFinite(force + torque + turnDirection)) {
      return Map.of();
    }
    var right = new Vec3(forward.z, 0, -forward.x).normalize();
    var fins =
        available.stream()
            .filter(fin -> fin.capacity > 0 && Double.isFinite(fin.capacity))
            .toList();
    if (Math.abs(turnDirection) > TURN_THRESHOLD) {
      fins = turningPair(fins, forward, right, Math.signum(turnDirection));
    }
    if (fins.isEmpty()) {
      return Map.of();
    }
    double leverScale =
        Math.max(
            1, fins.stream().mapToDouble(fin -> Math.abs(fin.offset.dot(right))).max().orElse(1));
    double capacityScale = fins.stream().mapToDouble(Fin::capacity).max().orElse(1);
    double m00 = REGULARIZATION, m01 = 0, m11 = REGULARIZATION;
    for (var fin : fins) {
      double weight = Math.pow(fin.capacity / capacityScale, 2);
      double lever = -fin.offset.dot(right) / leverScale;
      m00 += weight;
      m01 += weight * lever;
      m11 += weight * lever * lever;
    }
    double determinant = m00 * m11 - m01 * m01;
    double normalizedTorque = torque / leverScale;
    double a = (force * m11 - normalizedTorque * m01) / determinant;
    double b = (normalizedTorque * m00 - force * m01) / determinant;
    var result = new LinkedHashMap<BlockPos, Double>();
    double scale = 1;
    for (var fin : fins) {
      double weight = Math.pow(fin.capacity / capacityScale, 2);
      double thrust = weight * (a - b * fin.offset.dot(right) / leverScale);
      result.put(fin.socket, thrust);
      if (Math.abs(thrust) > fin.capacity) {
        scale = Math.min(scale, fin.capacity / Math.abs(thrust));
      }
    }
    // Reduce the whole requested wrench together: a weak fin must not make the strong one veer off.
    final double limit = scale;
    result.replaceAll((socket, thrust) -> thrust * limit);
    return Collections.unmodifiableMap(result);
  }

  private static List<Fin> turningPair(List<Fin> fins, Vec3 forward, Vec3 right, double turn) {
    var pair = new ArrayList<Fin>();
    for (int side : new int[] {-1, 1}) {
      Fin best = null;
      double score = -Double.MAX_VALUE;
      for (var fin : fins) {
        double lateral = fin.offset.dot(right);
        if (Math.signum(lateral) != side) {
          continue;
        }
        // Opposite diagonals reverse for the opposite turn; one paddle on each side provides yaw.
        double candidate = fin.offset.dot(forward) * -side * turn;
        if (candidate > score) {
          best = fin;
          score = candidate;
        }
      }
      if (best != null) {
        pair.add(best);
      }
    }
    if (pair.size() == 2) {
      return List.copyOf(pair);
    }
    // Incomplete layouts still get their physically available leverage, rather than invented
    // torque.
    return fins.stream()
        .sorted(Comparator.comparingDouble((Fin fin) -> -Math.abs(fin.offset.dot(right))))
        .limit(2)
        .toList();
  }
}
