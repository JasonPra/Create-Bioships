package org.Azkerash.create_bioships;

import java.util.Map;

/** Component health policy. Resource items inside containers never contribute structural health. */
final class BlockHealthTable {
  static final int CORE = 100, COMPONENT = 4;
  static final Map<String, Integer> VALUES =
      Map.of(
          "create_bioships:living_core", CORE,
          "create_bioships:stomach", 8,
          "create_bioships:living_hull", 12,
          "create_bioships:living_carapace", 12,
          "create_bioships:living_hull_tier_2", 20);

  static int value(String id) {
    return id.equals("minecraft:air")
            || id.equals("minecraft:cave_air")
            || id.equals("minecraft:void_air")
            || id.equals("minecraft:water")
            || id.equals("minecraft:lava")
        ? 0
        : VALUES.getOrDefault(id, COMPONENT);
  }

  private BlockHealthTable() {}
}
