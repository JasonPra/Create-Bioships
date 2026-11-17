package org.Azkerash.create_bioships.creature.survival;

import java.util.List;
import java.util.Set;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory.Feature;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory.Point;

/** Pure preferences. A positive score never substitutes for route or breathing validation. */
public final class EscapeScoring {
  private EscapeScoring() {}

  public record Threat(Point position, ThreatProfile profile) {}

  public record Candidate(
      Point position,
      Set<Feature> features,
      double confidence,
      boolean breaksSight,
      boolean safe,
      double danger,
      double prey) {
    public Candidate {
      features = Set.copyOf(features);
    }
  }

  public static double score(Point origin, Candidate candidate, List<Threat> threats) {
    if (!candidate.safe || threats.isEmpty()) return Double.NEGATIVE_INFINITY;
    double minimumGain = Double.POSITIVE_INFINITY, preferences = 0;
    double height = Math.clamp(candidate.position.y() - origin.y(), -6, 8);
    boolean water = candidate.features.contains(Feature.WATER);
    for (var threat : threats) {
      double gain = candidate.position.distance(threat.position) - origin.distance(threat.position);
      minimumGain = Math.min(minimumGain, gain);
      var p = threat.profile;
      // Water is useful against explosions only after the creature opens some blast distance.
      if (p.explosive() && gain < .5) return Double.NEGATIVE_INFINITY;
      if (p.explosive() && water) preferences += 9 * candidate.confidence;
      if (p.ranged()) {
        if (candidate.breaksSight) preferences += 14;
        if (candidate.features.contains(Feature.FOREST)) preferences += 2 * candidate.confidence;
      }
      if (!p.flying() && !p.aquatic())
        preferences += height * (p.climbing() ? .15 : p.jumping() ? .5 : 1.5);
      if (p.aquatic() && water) preferences -= 22;
      if (p.flying() && height > 0) preferences -= height * .5;
    }
    // Mixed threats are scored by the least improved separation, not just the nearest attacker.
    return minimumGain * 3
        + preferences / threats.size()
        - candidate.position.distance(origin) * .08
        - candidate.danger * 12
        + candidate.prey * .5;
  }
}
