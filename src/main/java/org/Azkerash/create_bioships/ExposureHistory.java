package org.Azkerash.create_bioships;

/**
 * One-second exposure estimate; short dry excursions decay three times more slowly than water use
 * builds.
 */
final class ExposureHistory {
  private int samples;
  private double score;

  void observe(boolean exposed) {
    samples = Math.min(3600, samples + 1);
    score += ((exposed ? 1000d : 0d) - score) / (exposed ? 40 : 120);
  }

  int samples() {
    return samples;
  }

  int score() {
    return (int) Math.round(score);
  }

  double preciseScore() {
    return score;
  }

  void restore(int samples, int score) {
    restore(samples, (double) score);
  }

  void restore(int samples, double score) {
    this.samples = Math.clamp(samples, 0, 3600);
    this.score = Double.isFinite(score) ? Math.clamp(score, 0, 1000) : 0;
  }
}
