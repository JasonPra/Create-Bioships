package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.Azkerash.create_bioships.core.SubLevelData;

/** Unrelated regression exhibits isolate their subjects from ambient life-support damage. */
public final class DevelopmentLifeSupport {
  private DevelopmentLifeSupport() {}

  public static void pause(ServerSubLevel body) {
    SubLevelData.hungerPaused(body, true);
    SubLevelData.oxygenPaused(body, true);
  }
}
