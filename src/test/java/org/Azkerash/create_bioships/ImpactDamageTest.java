package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ImpactDamageTest {
  @Test
  void restingAndRetreatingBodiesDoNotAddImpactDamage() {
    assertEquals(4, ImpactDamage.resolve(4, 0));
    assertEquals(4, ImpactDamage.resolve(4, -8));
  }

  @Test
  void ForwardMotionAddsABoundedBonus() {
    assertTrue(ImpactDamage.resolve(4, 2) > 4);
    assertEquals(12, ImpactDamage.resolve(4, 100));
  }

  @Test
  void InvalidPhysicsCannotCreateUnboundedDamage() {
    assertEquals(4, ImpactDamage.resolve(4, Double.NaN));
    assertEquals(0, ImpactDamage.resolve(Float.NaN, 8));
  }
}
