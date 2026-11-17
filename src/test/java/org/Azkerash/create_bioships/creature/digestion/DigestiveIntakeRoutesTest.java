package org.Azkerash.create_bioships.creature.digestion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell;
import org.junit.jupiter.api.Test;

class DigestiveIntakeRoutesTest {
  private static Cell c(int x, int z) {
    return new Cell(x, 0, z);
  }

  private static Map<Cell, Set<Cell>> links(Set<Cell> cells) {
    var result = new HashMap<Cell, Set<Cell>>();
    for (var p : cells) {
      var next = new HashSet<>(p.neighbors());
      next.retainAll(cells);
      result.put(p, next);
    }
    return result;
  }

  @Test
  void multipleMouthsCanFeedOneStomachWithoutExclusiveAssignments() {
    var cells = Set.of(c(0, 0), c(-1, 0), c(1, 0), c(2, 0));
    var graph =
        new DigestiveIntakeRoutes<>(
            Map.of(c(0, 0), "stomach"), Set.of(c(-1, 0), c(2, 0)), Set.of(c(1, 0)), links(cells));
    assertEquals("stomach", graph.route(c(-1, 0)).stomach());
    assertEquals("stomach", graph.route(c(2, 0)).stomach());
    assertEquals(List.of(c(1, 0)), graph.route(c(2, 0)).tubes());
  }

  @Test
  void oneMouthCanBranchToMultipleIndependentStomachs() {
    var cells = Set.of(c(0, 0), c(1, 0), c(2, 0), c(1, 1));
    var graph =
        new DigestiveIntakeRoutes<>(
            Map.of(c(0, 0), "left", c(2, 0), "right"),
            Set.of(c(1, 1)),
            Set.of(c(1, 0)),
            links(cells));
    assertEquals(
        Set.of("left", "right"),
        new HashSet<>(
            graph.routes(c(1, 1)).stream().map(DigestiveIntakeRoutes.Route::stomach).toList()));
  }

  @Test
  void brokenTubeImmediatelyRemovesItsIntakeOnly() {
    var cells = Set.of(c(0, 0), c(-1, 0), c(2, 0));
    var graph =
        new DigestiveIntakeRoutes<>(
            Map.of(c(0, 0), "stomach"), Set.of(c(-1, 0), c(2, 0)), Set.of(), links(cells));
    assertNotNull(graph.route(c(-1, 0)));
    assertNull(graph.route(c(2, 0)));
  }

  @Test
  void mouthsAndOtherOrgansAreNotTubeShortcuts() {
    var cells = Set.of(c(0, 0), c(1, 0), c(2, 0), c(3, 0));
    var graph =
        new DigestiveIntakeRoutes<>(
            Map.of(c(0, 0), "stomach"), Set.of(c(1, 0), c(3, 0)), Set.of(c(2, 0)), links(cells));
    assertNotNull(graph.route(c(1, 0)));
    assertNull(graph.route(c(3, 0)));
  }
}
