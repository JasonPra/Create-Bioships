package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LocomotionProfileTest {
  @Test
  void invalidDatapackValuesCannotCreateUnboundedSearchOrNonfinitePhysics() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LocomotionProfile(true, true, Double.NaN, .7, 24, 220, 1600));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LocomotionProfile(true, true, 1.7, .7, 24, 220, Integer.MAX_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LocomotionProfile(true, true, 1.7, .7, 24, 500, 200));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LocomotionProfile(true, true, 1.7, .1, 24, 220, 1600));
  }

  @Test
  void nonClingingLegProfilesRemainRepresentable() {
    var legs = new LocomotionProfile(false, false, 1, .6, 16, 100, 800);
    assertFalse(legs.walls());
    assertFalse(legs.ceilings());
  }
}
