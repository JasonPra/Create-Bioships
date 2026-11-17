package org.Azkerash.create_bioships;

import java.util.*;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;

/**
 * Shared reserve targets and discretionary-investment budget, separate from inventory mechanics.
 */
public final class EmergencyReserve {
  static final MaterialCost TARGET = new MaterialCost(32, 16, 8);

  static Map<String, Integer> targets() {
    return Map.of(MaterialCost.FLESH, 32, MaterialCost.FIBER, 16, MaterialCost.BONE, 8);
  }

  static boolean missing(LivingCoreBlockEntity core) {
    return targets().entrySet().stream()
        .anyMatch(e -> core.adaptation().material(e.getKey()) < e.getValue());
  }

  public static boolean after(LivingCoreBlockEntity core, MaterialCost price) {
    return new MaterialCost(32 + price.flesh(), 16 + price.fiber(), 8 + price.bone())
        .funded(BioshipStorage.account(core));
  }
}
