package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TrustParticleBudgetTest {
  @Test
  void rapidRenderingCannotExceedTheParticleCap() {
    var budget = new TrustParticleBudget();
    int spawned = 0;
    for (int frame = 0; frame < 200; frame++) if (budget.next(0, 100) >= 0) spawned++;
    assertEquals(16, spawned);
  }

  @Test
  void slowOrPausedRenderingDropsExpiredParticles() {
    var budget = new TrustParticleBudget();
    assertEquals(0, budget.next(0, 16));
    assertEquals(-1, budget.next(TrustParticleBudget.MAX_AGE_NANOS + 1, 16));
  }

  @Test
  void noBurstCanContinueAfterTwentyFrames() {
    var budget = new TrustParticleBudget();
    for (int frame = 0; frame < 20; frame++) budget.next(0, 0);
    assertEquals(-1, budget.next(0, 16));
  }
}
