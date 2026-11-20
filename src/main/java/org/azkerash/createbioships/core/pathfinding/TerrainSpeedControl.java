package org.azkerash.createbioships.core.pathfinding;

/**
 * Ground-navigation speed policy; uses existing support samples rather than extra terrain casts.
 */
public final class TerrainSpeedControl {
  private static final double LEVEL_TOLERANCE_BLOCKS = .05;
  private static final double UPHILL_GAIN = 2;
  private static final double DOWNHILL_GAIN = 3;
  private static final double MIN_SPEED_FACTOR = .25;
  private static final double RECOVERY_PER_SECOND = 1;

  private TerrainSpeedControl() {}

  public static double factor(double heightChange, double horizontalDistance) {
    if (!Double.isFinite(heightChange + horizontalDistance) || horizontalDistance <= 0) {
      return MIN_SPEED_FACTOR;
    }
    if (Math.abs(heightChange) <= LEVEL_TOLERANCE_BLOCKS) {
      return 1;
    }
    double grade = Math.abs(heightChange) / horizontalDistance;
    double gain = heightChange > 0 ? UPHILL_GAIN : DOWNHILL_GAIN;
    return Math.clamp(1 / (1 + gain * grade), MIN_SPEED_FACTOR, 1);
  }

  /** Brake as soon as the slope is visible; release the cap gradually when the route levels out. */
  public static double recover(double previous, double requested, double seconds) {
    return Math.min(requested, previous + Math.max(0, seconds) * RECOVERY_PER_SECOND);
  }
}
