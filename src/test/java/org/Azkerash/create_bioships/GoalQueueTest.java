package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class GoalQueueTest {
  private static BehaviorPriority.Offer offer(String key, BehaviorPriority.Band band, int urgency) {
    return new BehaviorPriority.Offer(key, band, urgency, 0, 0);
  }

  @Test
  void queuesMultipleNeedsBeforeGoalsAndDropsIneligibleEntries() {
    var queue = new GoalQueue<BehaviorPriority.Offer>();
    var food = offer("food", BehaviorPriority.Band.NEED, 950);
    var grow = offer("grow", BehaviorPriority.Band.GOAL, 1000);
    queue.refresh(List.of(grow, food), o -> o, "");
    assertEquals(List.of(food, grow), queue.pending());
    queue.refresh(List.of(grow), o -> o, "food");
    assertEquals(List.of(grow), queue.pending());
  }

  @Test
  void refreshedUrgencyReordersGoalsWithoutDuplicatingTargets() {
    var queue = new GoalQueue<BehaviorPriority.Offer>();
    var old = offer("sac", BehaviorPriority.Band.GOAL, 300);
    var urgent = offer("sac", BehaviorPriority.Band.NEED, 980);
    var hunt = offer("hunt", BehaviorPriority.Band.NEED, 880);
    queue.refresh(List.of(old, urgent, hunt), o -> o, "hunt");
    assertEquals(List.of(urgent, hunt), queue.pending());
    assertThrows(UnsupportedOperationException.class, () -> queue.pending().clear());
  }
}
