package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MaterialRenderPassTest {
  @Test
  void textureSwitchesInvalidateOldConsumersButConsecutivePassesRemainValid() {
    var source = new SwitchingBuffers();
    for (int i = 0; i < 30; i++)
      MaterialRenderPass.draw(
          source::acquire,
          "skin",
          "suckers",
          buffer -> buffer.vertices(24),
          buffer -> buffer.vertices(120),
          i % 2 == 0);
    assertEquals(30 * 24 + 15 * 120, source.vertices);
    assertTrue(source.switches > 20);
  }

  @Test
  void regressionFixtureActuallyRejectsStaleBufferUse() {
    var source = new SwitchingBuffers();
    var stale = source.acquire("skin");
    source.acquire("suckers");
    assertThrows(IllegalStateException.class, () -> stale.vertices(1));
  }

  private static final class SwitchingBuffers {
    String current;
    int generation, switches, vertices;

    Lease acquire(String material) {
      if (!material.equals(current)) {
        current = material;
        generation++;
        switches++;
      }
      return new Lease(this, generation);
    }
  }

  private record Lease(SwitchingBuffers source, int generation) {
    void vertices(int count) {
      if (source.generation != generation) throw new IllegalStateException("Not building");
      source.vertices += count;
    }
  }
}
