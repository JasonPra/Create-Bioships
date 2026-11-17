package org.Azkerash.create_bioships.disabled.wip;


import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PilotAuthorityTest {
  @Test
  void trustedActivePilotOverridesRetreat() {
    assertTrue(PilotAuthority.obeys(80, true, true, false, 100, 300));
    assertTrue(PilotAuthority.obeys(100, true, true, false, 299, 300));
    assertTrue(PilotAuthority.obeys(100, true, true, true, 400, 300));
    assertFalse(PilotAuthority.obeys(79, true, true, false, 100, 300));
  }

  @Test
  void oldOrUnauthorizedCommandsDoNotSuppressSurvival() {
    assertFalse(PilotAuthority.obeys(100, true, true, false, 300, 300));
    assertFalse(PilotAuthority.obeys(100, true, true, false, 400, 300));
    assertFalse(PilotAuthority.obeys(100, false, true, false, 100, 300));
    assertFalse(PilotAuthority.obeys(100, true, false, true, 100, 300));
  }
}
