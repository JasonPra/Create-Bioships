package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BlockEnergyTableTest {
  @Test
  void producersAndConnectorsHaveNoUpkeep() {
    for (var id :
        new String[] {
          "create_bioships:living_core",
          "create_bioships:feeding_mouth",
          "create_bioships:stomach",
          "create_bioships:fleshy_intestine"
        }) assertEquals(0, BlockEnergyTable.units(id));
  }

  @Test
  void demandAndDigestiveCapacityScaleFromTheSameUnits() {
    assertEquals(1, BlockEnergyTable.units("biomancy:decomposer"));
    assertEquals(1, BlockEnergyTable.units("biomancy:digester"));
    assertEquals(0, BlockEnergyTable.demand(0));
    assertEquals(1, BlockEnergyTable.demand(BlockEnergyTable.units("biomancy:storage_sac")));
    assertEquals(2, BlockEnergyTable.demand(65));
    assertEquals(2, BlockEnergyTable.pairs(BlockEnergyTable.demand(257)));
  }
}
