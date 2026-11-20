package org.Azkerash.create_bioships.compat.epicfight;

import net.neoforged.fml.ModList;

/** Optional integration flag, safe to query without loading any Epic Fight API classes. */
public final class EpicFightIntegration {
  private static final boolean AVAILABLE = ModList.get().isLoaded("epicfight");

  private EpicFightIntegration() {}

  public static boolean isAvailable() {
    return AVAILABLE;
  }
}
