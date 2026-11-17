package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComponentMergingTest {
  @Test
  void internalMoveAbsorbsAndRedeploysSymmetrically() {
    assertEquals(1, ComponentMerging.Animation.scale(0, false));
    assertEquals(1, ComponentMerging.Animation.scale(1, false));
    assertEquals(.025, ComponentMerging.Animation.scale(.5, false), 1e-9);
    for (int i = 0; i <= 100; i++)
      assertEquals(
          ComponentMerging.Animation.scale(i / 100.0, false),
          ComponentMerging.Animation.scale(1 - i / 100.0, false),
          1e-9);
  }

  @Test
  void swallowingKeepsApproachVisibleAndAbsorbsAtArrival() {
    assertEquals(1, ComponentMerging.Animation.scale(.5, true));
    double previous = 1;
    for (int i = 0; i <= 100; i++) {
      double scale = ComponentMerging.Animation.scale(i / 100.0, true);
      assertTrue(scale <= previous + 1e-9 && scale >= .025);
      previous = scale;
    }
    assertEquals(.025, previous, 1e-9);
  }

  @Test
  void malformedOrDelayedTimeNeverInvertsTheModel() {
    assertEquals(1, ComponentMerging.Animation.scale(Double.NaN, false));
    assertEquals(1, ComponentMerging.Animation.scale(-1, false));
    assertEquals(1, ComponentMerging.Animation.scale(2, false));
    assertTrue(Double.isFinite(ComponentMerging.Animation.scale(Double.POSITIVE_INFINITY, true)));
  }
}
