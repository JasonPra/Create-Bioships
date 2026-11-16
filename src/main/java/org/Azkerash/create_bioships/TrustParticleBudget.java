package org.Azkerash.create_bioships;

/** Rendering budget independent of Minecraft, shared with deterministic validation. */
public final class TrustParticleBudget {
  public static final int PARTICLES = 16, FRAMES = 20;
  public static final long MAX_AGE_NANOS = 1_000_000_000L;
  private int frame, point;

  public int next(long ageNanos, int available) {
    if (ageNanos < 0
        || ageNanos > MAX_AGE_NANOS
        || frame++ >= FRAMES
        || point >= Math.min(PARTICLES, available)) return -1;
    return point++;
  }
}
