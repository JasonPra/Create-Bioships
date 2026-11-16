package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EngineRoomSizingTest {
  @Test
  void capacityGrowsInPairedSideBaysWithoutShrinkingTheBaseRoom() {
    assertEquals(1, EngineRoomSizing.columns(0));
    assertEquals(1, EngineRoomSizing.columns(2));
    assertEquals(2, EngineRoomSizing.columns(3));
    assertEquals(2, EngineRoomSizing.columns(4));
    assertEquals(3, EngineRoomSizing.columns(5));
    for (int pairs = 1; pairs <= 22; pairs++) {
      int columns = EngineRoomSizing.columns(pairs);
      assertTrue(EngineRoomSizing.capacity(columns) >= pairs);
      assertTrue(EngineRoomSizing.capacity(columns) < pairs + 2);
      assertTrue(EngineRoomSizing.width(columns) <= 49);
    }
  }
}
