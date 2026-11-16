package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LegUsageHistoryTest {
  @Test
  void briefDipDoesNotReplaceWalkingLegs() {
    var history = new LegUsageHistory();
    for (int i = 0; i < 10; i++) history.observe(true);
    assertEquals(0, history.desired(4, 0));
    for (int i = 0; i < 80; i++) history.observe(false);
    assertEquals(0, history.desired(4, 0));
  }

  @Test
  void sustainedSwimmingGraduallyFavorsPaddlesAndSurvivesRestore() {
    var history = new LegUsageHistory();
    for (int i = 0; i < 300; i++) history.observe(true);
    assertEquals(4, history.desired(4, 0));
    var restored = new LegUsageHistory();
    restored.restore(history.samples(), history.water());
    assertEquals(history.desired(4, 2), restored.desired(4, 2));
  }

  @Test
  void mixedUseKeepsExistingAdaptationUntilLandUseIsSustained() {
    var history = new LegUsageHistory();
    history.restore(100, 500);
    assertEquals(2, history.desired(4, 2));
    for (int i = 0; i < 120; i++) history.observe(false);
    assertEquals(0, history.desired(4, 2));
  }

  @Test
  void continuousWaterReachesFullScoreInsteadOfRoundingPlateau() {
    var history = new LegUsageHistory();
    for (int i = 0; i < 400; i++) history.observe(true);
    assertEquals(1000, history.water());
    history.restore(400, 961);
    for (int i = 0; i < 200; i++) history.observe(true);
    assertEquals(1000, history.water());
  }

  @Test
  void briefDryHopsDecaySlowlyButSustainedLandUseStillReversesAdaptation() {
    var history = new LegUsageHistory();
    history.restore(400, 1000);
    history.observe(false);
    assertEquals(992, history.water());
    for (int i = 1; i < 10; i++) history.observe(false);
    assertTrue(history.water() >= 900);
    assertEquals(4, history.desired(4, 4));
    for (int i = 10; i < 240; i++) history.observe(false);
    assertEquals(0, history.desired(4, 4));
  }

  @Test
  void fractionalHistorySurvivesRestoreWithoutChangingFutureDecisions() {
    var original = new LegUsageHistory();
    for (int i = 0; i < 100; i++) original.observe(i % 3 != 0);
    var restored = new LegUsageHistory();
    restored.restore(original.samples(), original.preciseWater());
    for (int i = 0; i < 100; i++) {
      original.observe(i % 5 != 0);
      restored.observe(i % 5 != 0);
      assertEquals(original.preciseWater(), restored.preciseWater());
      assertEquals(original.desired(4, 2), restored.desired(4, 2));
    }
  }
}
