package org.Azkerash.create_bioships.creature.digestion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell;
import org.junit.jupiter.api.Test;

class StomachFormationTest {
  @Test
  void formedEfficiencyAddsWorkWithoutChangingRecipesOrStackingBonuses() {
    int ordinary = 0, formed = 0;
    for (int tick = 1; tick <= 80; tick++) {
      ordinary += StomachFormation.workUnits(tick, false);
      formed += StomachFormation.workUnits(tick, true);
    }
    assertEquals(80, ordinary);
    assertEquals(100, formed);
  }

  @Test
  void solidCubeFormsButMissingCenterDoesNotAndRebuildRestoresMembership() {
    var origin = new Cell(-3, 7, 2);
    var nodes = new HashMap<Cell, Object>();
    for (var p : StomachFormation.cells(origin)) nodes.put(p, new Object());
    var cube = StomachFormation.detect(nodes).getFirst();
    assertEquals(origin, cube.origin());
    assertEquals(27, cube.members().size());
    nodes.forEach((p, owner) -> assertSame(owner, cube.members().get(p)));
    var center = new Cell(-2, 8, 3);
    var removed = nodes.remove(center);
    assertTrue(StomachFormation.detect(nodes).isEmpty());
    nodes.put(center, removed);
    assertEquals(1, StomachFormation.detect(nodes).size());
  }

  @Test
  void overlappingCubesCannotDoubleCountOrgans() {
    var nodes = new HashMap<Cell, String>();
    for (int x = 0; x < 4; x++)
      for (int y = 0; y < 3; y++)
        for (int z = 0; z < 3; z++) nodes.put(new Cell(x, y, z), x + ":" + y + ":" + z);
    assertEquals(1, StomachFormation.detect(nodes).size());
    for (int x = 4; x < 6; x++)
      for (int y = 0; y < 3; y++)
        for (int z = 0; z < 3; z++) nodes.put(new Cell(x, y, z), x + ":" + y + ":" + z);
    var cubes = StomachFormation.detect(nodes);
    assertEquals(2, cubes.size());
    assertEquals(54, cubes.stream().flatMap(c -> c.members().keySet().stream()).distinct().count());
  }
}
