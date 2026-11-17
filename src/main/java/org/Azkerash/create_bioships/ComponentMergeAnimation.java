package org.Azkerash.create_bioships;

/** Shared visual envelope for amoeba absorption and internal redeployment. No physical state. */
public final class ComponentMergeAnimation {
  private ComponentMergeAnimation() {}

  public static double scale(double progress, boolean swallowed) {
    double t = Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
    double edge = swallowed ? (1 - t) / .25 : Math.min(t, 1 - t) / .2;
    edge = Math.clamp(edge, 0, 1);
    double smooth = edge * edge * (3 - 2 * edge);
    return swallowed ? .025 + .975 * smooth : 1 - .975 * smooth;
  }
}
