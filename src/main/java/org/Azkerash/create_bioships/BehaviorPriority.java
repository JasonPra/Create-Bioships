package org.Azkerash.create_bioships;

import java.util.*;

/** Needs and goals are separate bands: proximity or inertia cannot promote a goal above a need. */
final class BehaviorPriority {
  enum Band {
    IDLE,
    GOAL,
    NEED,
    SAFETY
  }

  record Offer(String key, Band band, int urgency, int value, double effort) {
    double weight(String previous) {
      return urgency * 1000d + value - Math.clamp(effort, 0, 200) + (key.equals(previous) ? 12 : 0);
    }
  }

  static Offer choose(Collection<Offer> offers, String previous) {
    return offers.stream()
        .max(
            Comparator.<Offer>comparingInt(o -> o.band().ordinal())
                .thenComparingDouble(o -> o.weight(previous))
                .thenComparing(Offer::key))
        .orElse(null);
  }

  private BehaviorPriority() {}
}
