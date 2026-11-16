package org.Azkerash.create_bioships;

import java.util.*;

/** Pure reusable energy policy; unlisted ordinary blocks contribute one structural unit. */
final class BlockEnergyTable {
  static final Map<String, Integer> COSTS =
      Map.ofEntries(
          Map.entry("create_bioships:living_core", 0),
          Map.entry("create_bioships:feeding_mouth", 0),
          Map.entry("create_bioships:stomach", 0),
          Map.entry("create_bioships:fleshy_intestine", 0),
          Map.entry("biomancy:maw_hopper", 0));

  static int units(String id) {
    return COSTS.getOrDefault(id, 1);
  }

  static int demand(int units) {
    return Math.max(0, (units + 63) / 64);
  }

  static int pairs(int demand) {
    return Math.max(1, (demand + 3) / 4);
  }
}
