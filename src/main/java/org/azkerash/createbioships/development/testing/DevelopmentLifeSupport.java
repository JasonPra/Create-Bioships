package org.azkerash.createbioships.development.testing;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.azkerash.createbioships.core.SubLevelData;

/** Unrelated regression exhibits isolate their subjects from ambient life-support damage. */
public final class DevelopmentLifeSupport {
  private DevelopmentLifeSupport() {}

  public static void pause(ServerSubLevel body) {
    SubLevelData.hungerPaused(body, true);
    SubLevelData.oxygenPaused(body, true);
  }
}
