package org.Azkerash.create_bioships.disabled.wip;


import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FlagellumMotionTest {
  @Test
  void noFlagellaMeansNoPropulsionAtEveryStrokePhase() {
    for (int tick = 0; tick < 60; tick++) assertEquals(0, FlagellumMotion.strength(0, tick));
  }

  @Test
  void occupiedOrMissingLimbsReduceThrustProportionally() {
    for (int tick = 0; tick < 60; tick++)
      assertEquals(
          FlagellumMotion.strength(6, tick) / 2, FlagellumMotion.strength(3, tick), 0.000001);
  }

  @Test
  void strokeCyclesStayBoundedAndDoNotReverseThrust() {
    double low = 1, high = 0;
    for (int tick = 0; tick < 60; tick++) {
      double strength = FlagellumMotion.strength(6, tick);
      assertTrue(strength >= 0.75 && strength <= 1);
      low = Math.min(low, strength);
      high = Math.max(high, strength);
    }
    assertTrue(high - low > 0.2);
  }
}
