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
    for (int i = 0; i < 80; i++) history.observe(false);
    assertEquals(0, history.desired(4, 2));
  }
}
