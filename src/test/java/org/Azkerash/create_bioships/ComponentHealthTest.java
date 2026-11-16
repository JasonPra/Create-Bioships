package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComponentHealthTest {
  @Test
  void coreAndEachSmallComponentAddHealthImmediately() {
    assertEquals(
        104,
        BlockHealthTable.value("create_bioships:living_core")
            + BlockHealthTable.value("biomancy:packed_flesh"));
    assertEquals(0, BlockHealthTable.value("minecraft:air"));
    assertEquals(4, BlockHealthTable.value("othermod:new_component"));
  }

  @Test
  void armorHasProgressivelyMoreHealthThanTissue() {
    assertTrue(
        BlockHealthTable.value("create_bioships:living_hull")
            > BlockHealthTable.value("biomancy:packed_flesh"));
    assertTrue(
        BlockHealthTable.value("create_bioships:living_hull_tier_2")
            > BlockHealthTable.value("create_bioships:living_hull"));
  }

  @Test
  void combinedStomachRetainsBothOriginalOrganContributions() {
    assertEquals(
        BlockHealthTable.value("biomancy:decomposer") + BlockHealthTable.value("biomancy:digester"),
        BlockHealthTable.value("create_bioships:stomach"));
  }
}
