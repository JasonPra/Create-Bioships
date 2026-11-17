package org.Azkerash.create_bioships.creature.navigation;

/** Bounded velocity feedback learns buoyancy/drag instead of adding gravity cancellation twice. */
public final class SwimVelocityControl {
  public record Vector(double x, double y, double z) {}

  private final double[] correction = new double[3];

  public Vector acceleration(Vector wanted, Vector actual, double dt) {
    if (!Double.isFinite(dt) || dt <= 0) return new Vector(0, 0, 0);
    double[] error = {wanted.x - actual.x, wanted.y - actual.y, wanted.z - actual.z};
    double[] result = new double[3];
    for (int axis = 0; axis < 3; axis++) {
      if (!Double.isFinite(error[axis])) return new Vector(0, 0, 0);
      correction[axis] = Math.clamp(correction[axis] + error[axis] * 8 * Math.min(dt, .1), -20, 20);
      result[axis] = Math.clamp(error[axis] * 12 + correction[axis], -36, 36);
    }
    return new Vector(result[0], result[1], result[2]);
  }
}
