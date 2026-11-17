package org.Azkerash.create_bioships.creature.survival;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory.*;
import org.junit.jupiter.api.Test;

class EscapeScoringTest {
  final Point origin = new Point(4, 0, 0);
  final Point enemy = new Point(0, 0, 0);

  EscapeScoring.Candidate candidate(
      double x, double y, boolean water, boolean cover, boolean safe) {
    return new EscapeScoring.Candidate(
        new Point(x, y, 0), water ? Set.of(Feature.WATER) : Set.of(), 1, cover, safe, 0, 0);
  }

  double score(EscapeScoring.Candidate c, ThreatProfile p) {
    return EscapeScoring.score(origin, c, List.of(new EscapeScoring.Threat(enemy, p)));
  }

  @Test
  void explosionsPreferWaterButNeverApproachTheBlast() {
    var p = new ThreatProfile(true, false, false, false, false, false);
    assertTrue(
        score(candidate(12, 0, true, false, true), p)
            > score(candidate(12, 0, false, false, true), p));
    assertEquals(Double.NEGATIVE_INFINITY, score(candidate(3, 0, true, false, true), p));
  }

  @Test
  void allRangedAttackersPreferVerifiedCover() {
    var p = new ThreatProfile(false, true, false, false, false, false);
    assertTrue(
        score(candidate(10, 0, false, true, true), p)
            > score(candidate(12, 0, false, false, true), p));
  }

  @Test
  void climbingAndFlyingEnemiesReduceHeightAdvantage() {
    var high = candidate(10, 5, false, false, true);
    assertTrue(
        score(high, ThreatProfile.GROUND)
            > score(high, new ThreatProfile(false, false, true, false, false, false)));
    assertTrue(
        score(high, ThreatProfile.GROUND)
            > score(high, new ThreatProfile(false, false, false, true, false, false)));
  }

  @Test
  void aquaticPursuersMakeWaterUnattractive() {
    var p = new ThreatProfile(false, true, false, false, true, false);
    assertTrue(
        score(candidate(12, 0, false, false, true), p)
            > score(candidate(12, 0, true, false, true), p));
  }

  @Test
  void hazardousDestinationsAreRejected() {
    assertEquals(
        Double.NEGATIVE_INFINITY,
        score(candidate(12, 0, false, true, false), ThreatProfile.GROUND));
  }

  @Test
  void mixedThreatsDoNotIgnoreEnemyOnEscapeSide() {
    var threats =
        List.of(
            new EscapeScoring.Threat(enemy, ThreatProfile.GROUND),
            new EscapeScoring.Threat(new Point(14, 0, 0), ThreatProfile.GROUND));
    assertTrue(
        EscapeScoring.score(origin, candidate(6, 0, false, false, true), threats)
            > EscapeScoring.score(origin, candidate(12, 0, false, false, true), threats));
  }
}
