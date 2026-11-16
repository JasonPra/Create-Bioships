package org.Azkerash.create_bioships;

/** Collision-only damage policy: forward object speed adds a bounded impact bonus. */
final class ImpactDamage {
  static float resolve(float base, double closingSpeed) {
    if (!Float.isFinite(base) || base <= 0) return 0;
    if (!Double.isFinite(closingSpeed)) closingSpeed = 0;
    double bonus = Math.min(base * 2, Math.max(0, closingSpeed - .25) * 1.5);
    return base + (float) bonus;
  }
}
