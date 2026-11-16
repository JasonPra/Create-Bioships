package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SurvivalAdaptationsTest {
  @Test
  void survivalIsWorthMoreThanOneMealAndResistanceIsBounded() {
    var meat = new BioshipSurvivalAdaptations();
    var survivor = new BioshipSurvivalAdaptations();
    meat.ateCreeperMeat(1);
    survivor.survivedExplosion();
    assertTrue(survivor.blastPoints() > meat.blastPoints());
    assertTrue(survivor.blockResistance(2) > meat.blockResistance(2));
    meat.ateCreeperMeat(Integer.MAX_VALUE);
    assertEquals(100, meat.blastPoints());
    assertEquals(50, meat.damage(100));
    meat.ateCreeperMeat(-3);
    assertEquals(100, meat.blastPoints());
  }
}
