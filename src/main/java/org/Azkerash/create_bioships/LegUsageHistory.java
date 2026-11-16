package org.Azkerash.create_bioships;

/** One-second duty-cycle samples; hysteresis prevents expensive specialization after brief dips. */
final class LegUsageHistory {
  private final ExposureHistory history = new ExposureHistory();

  void observe(boolean wet) {
    history.observe(wet);
  }

  int samples() {
    return history.samples();
  }

  int water() {
    return history.score();
  }

  void restore(int samples, int water) {
    history.restore(samples, water);
  }

  int desired(int legs, int current) {
    if (legs <= 0) return 0;
    if (samples() < 40) return current;
    if (water() >= 650) return Math.clamp((int) Math.round(legs * (water() - 450) / 550d), 1, legs);
    if (water() <= 250) return 0;
    return current;
  }
}
