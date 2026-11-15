package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BehaviorPriorityTest {
  private static BehaviorPriority.Offer offer(
      String key, BehaviorPriority.Band band, int urgency, double effort) {
    return new BehaviorPriority.Offer(key, band, urgency, 0, effort);
  }

  @Test
  void aGoalCannotOutweighAnUrgentNeed() {
    var need = offer("eat", BehaviorPriority.Band.NEED, 1, 200);
    var goal = offer("grow", BehaviorPriority.Band.GOAL, 999, 0);
    assertEquals(need, BehaviorPriority.choose(List.of(need, goal), "grow"));
  }

  @Test
  void escapeInterruptsHuntingAndScavenging() {
    var escape = offer("escape", BehaviorPriority.Band.SAFETY, 100, 80);
    assertEquals(
        escape,
        BehaviorPriority.choose(
            List.of(
                escape,
                offer("hunt", BehaviorPriority.Band.NEED, 900, 0),
                offer("wreck", BehaviorPriority.Band.NEED, 950, 0)),
            "wreck"));
  }

  @Test
  void urgentFoodBeatsOptionalMaterialCollection() {
    var eat = offer("food", BehaviorPriority.Band.NEED, 900, 10);
    assertEquals(
        eat,
        BehaviorPriority.choose(
            List.of(eat, offer("optional", BehaviorPriority.Band.GOAL, 1000, 0)), "optional"));
  }

  @Test
  void usefulWreckBeatsLiveHuntingDespiteDistance() {
    var wreck = offer("wreck", BehaviorPriority.Band.NEED, 800, 100);
    assertEquals(
        wreck,
        BehaviorPriority.choose(
            List.of(wreck, offer("hunt", BehaviorPriority.Band.NEED, 700, 0)), "hunt"));
  }

  @Test
  void stableEqualChoicesDoNotOscillate() {
    var first = offer("a", BehaviorPriority.Band.GOAL, 200, 10);
    var second = offer("b", BehaviorPriority.Band.GOAL, 200, 9);
    assertEquals(first, BehaviorPriority.choose(List.of(first, second), "a"));
  }

  @Test
  void removedAndSatisfiedNeedsCannotRemainSelected() {
    var wander = offer("wander", BehaviorPriority.Band.IDLE, 0, 0);
    assertEquals(wander, BehaviorPriority.choose(List.of(wander), "food"));
  }
}
