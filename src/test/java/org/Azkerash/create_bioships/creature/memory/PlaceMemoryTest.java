package org.Azkerash.create_bioships.creature.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.Azkerash.create_bioships.creature.memory.PlaceMemory.*;
import org.junit.jupiter.api.Test;

class PlaceMemoryTest {
  private static Point p(double x) {
    return new Point(x, 80, 8);
  }

  @Test
  void mergesNearbyEvidenceButKeepsDimensionsAndElevationSeparate() {
    var memory = new PlaceMemory();
    memory.terrain("overworld", p(0), "plains", Set.of(Feature.WATER), 10);
    memory.prey("overworld", p(2), "plains", "cow", 20);
    memory.terrain("nether", p(0), "basalt", Set.of(), 30);
    memory.terrain("overworld", new Point(0, 96, 8), "plains", Set.of(Feature.LEDGE), 40);
    assertEquals(3, memory.size());
    assertEquals(2, memory.inDimension("overworld").size());
    var water =
        memory.snapshot().stream()
            .filter(e -> e.features().contains(Feature.WATER))
            .findFirst()
            .orElseThrow();
    assertTrue(water.prey().containsKey("cow"));
    assertEquals(10, water.terrainSeen());
  }

  @Test
  void terrainRefreshReplacesObsoleteFeaturesButKeepsPreyHistory() {
    var memory = new PlaceMemory();
    memory.terrain("world", p(0), "plains", Set.of(Feature.WATER), 10);
    memory.prey("world", p(0), "plains", "pig", 20);
    memory.terrain("world", p(0), "plains", Set.of(), 30);
    assertTrue(memory.snapshot().getFirst().features().isEmpty());
    assertTrue(memory.snapshot().getFirst().prey().containsKey("pig"));
  }

  @Test
  void oldestPlacesAreEvictedAndSnapshotsCannotMutateStorage() {
    var memory = new PlaceMemory(2);
    for (int i = 0; i < 3; i++) memory.terrain("world", p(i * 32), "plains", Set.of(), i);
    assertEquals(2, memory.size());
    assertEquals(32, memory.snapshot().getFirst().point().x());
    assertThrows(UnsupportedOperationException.class, () -> memory.snapshot().clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> memory.snapshot().getFirst().prey().put("cow", 1L));
  }

  @Test
  void preyAndDangerBecomeStaleWithoutErasingHistoricalHabitatKnowledge() {
    var memory = new PlaceMemory();
    memory.terrain("world", p(0), "forest", Set.of(Feature.FOREST), 0);
    memory.prey("world", p(0), "forest", "cow", 0);
    memory.danger("world", p(0), "forest", "ranged", 0);
    var place = memory.snapshot().getFirst();
    assertEquals(1, place.preyConfidence(0));
    assertEquals(0, place.preyConfidence(6000));
    assertEquals(0, place.dangerConfidence(1200));
    assertTrue(place.prey().containsKey("cow"));
    assertTrue(place.terrainConfidence(1_000_000) > 0);
  }

  @Test
  void restoreRetainsEvidenceAndAppliesBounds() {
    var original = new PlaceMemory();
    for (int i = 0; i < 4; i++) original.prey("world", p(i * 32), "plains", "cow", i);
    var restored = new PlaceMemory(2);
    restored.restore(original.snapshot());
    assertEquals(2, restored.size());
    assertTrue(restored.snapshot().getFirst().prey().containsKey("cow"));
  }

  @Test
  void malformedCoordinatesAreRejectedAndSpeciesListsAreBounded() {
    assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
    var memory = new PlaceMemory();
    for (int i = 0; i < 30; i++) memory.prey("world", p(0), "plains", "species" + i, i);
    assertEquals(16, memory.snapshot().getFirst().prey().size());
  }
}
