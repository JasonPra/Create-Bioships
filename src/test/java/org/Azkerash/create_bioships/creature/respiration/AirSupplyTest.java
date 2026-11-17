package org.Azkerash.create_bioships.creature.respiration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AirSupplyTest {
  @Test
  void seeksAirBeforeDrowningAndWaitsForACompleteBreath() {
    var supply = new AirSupply();
    for (int i = 0; i < 101; i++) supply.tick(false, false, false);
    assertEquals(199, supply.remaining());
    assertTrue(supply.needsAir(false, false));
    supply.tick(true, true, false);
    assertTrue(supply.needsAir(true, true));
    for (int i = 0; i < 25; i++) supply.tick(true, true, false);
    assertEquals(300, supply.remaining());
    assertFalse(supply.needsAir(true, true));
  }

  @Test
  void workingGillsCancelAirSeekingWithoutRequiringABreach() {
    var supply = new AirSupply();
    supply.restore(100, true);
    supply.tick(true, false, true);
    assertEquals(104, supply.remaining());
    assertFalse(supply.seeking());
  }

  @Test
  void airAccessWithoutFunctionalBreathingDoesNotReplenishAir() {
    var supply = new AirSupply();
    supply.restore(100, true);
    supply.tick(false, true, false);
    assertEquals(99, supply.remaining());
    assertFalse(supply.needsAir(false, true));
  }

  @Test
  void savedLowAirRetainsUrgencyAndNeverRefillsByReloading() {
    var supply = new AirSupply();
    supply.restore(75, true);
    assertEquals(75, supply.remaining());
    assertTrue(supply.needsAir(false, false));
    for (int i = 0; i < 100; i++) supply.tick(false, false, false);
    assertEquals(0, supply.remaining());
    assertTrue(supply.seeking());
  }
}
