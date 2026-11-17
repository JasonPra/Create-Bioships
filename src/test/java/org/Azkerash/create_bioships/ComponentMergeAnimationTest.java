package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComponentMergeAnimationTest {
  @Test
  void internalMoveAbsorbsAndRedeploysSymmetrically() {
    assertEquals(1, ComponentMergeAnimation.scale(0, false));
    assertEquals(1, ComponentMergeAnimation.scale(1, false));
    assertEquals(.025, ComponentMergeAnimation.scale(.5, false), 1e-9);
    for (int i = 0; i <= 100; i++)
      assertEquals(
          ComponentMergeAnimation.scale(i / 100.0, false),
          ComponentMergeAnimation.scale(1 - i / 100.0, false),
          1e-9);
  }

  @Test
  void swallowingKeepsApproachVisibleAndAbsorbsAtArrival() {
    assertEquals(1, ComponentMergeAnimation.scale(.5, true));
    double previous = 1;
    for (int i = 0; i <= 100; i++) {
      double scale = ComponentMergeAnimation.scale(i / 100.0, true);
      assertTrue(scale <= previous + 1e-9 && scale >= .025);
      previous = scale;
    }
    assertEquals(.025, previous, 1e-9);
  }

  @Test
  void malformedOrDelayedTimeNeverInvertsTheModel() {
    assertEquals(1, ComponentMergeAnimation.scale(Double.NaN, false));
    assertEquals(1, ComponentMergeAnimation.scale(-1, false));
    assertEquals(1, ComponentMergeAnimation.scale(2, false));
    assertTrue(Double.isFinite(ComponentMergeAnimation.scale(Double.POSITIVE_INFINITY, true)));
  }
}
