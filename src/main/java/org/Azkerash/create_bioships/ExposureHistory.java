package org.Azkerash.create_bioships;

/** Reusable bounded one-second exposure history, independent of the component consuming it. */
final class ExposureHistory {
  private int samples, score;

  void observe(boolean exposed) {
    samples = Math.min(3600, samples + 1);
    score = Math.clamp((score * 39 + (exposed ? 1000 : 0)) / 40, 0, 1000);
  }

  int samples() {
    return samples;
  }

  int score() {
    return score;
  }

  void restore(int samples, int score) {
    this.samples = Math.clamp(samples, 0, 3600);
    this.score = Math.clamp(score, 0, 1000);
  }
}
