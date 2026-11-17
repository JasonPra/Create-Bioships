package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;

/** Sable-to-energy adapter; all consumers share the same table and tick snapshot. */
public final class BioshipEnergy {
  private record Snapshot(long tick, int units) {}

  private static final Map<LivingCoreBlockEntity, Snapshot> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  static int demand(LivingCoreBlockEntity core) {
    return BlockEnergyTable.demand(units(core));
  }

  public static int units(LivingCoreBlockEntity core) {
    var level = core.getLevel();
    if (core.ship() == null || level == null) return 0;
    long now = level.getGameTime();
    var old = CACHE.get(core);
    if (old != null && old.tick() == now) return old.units();
    int units =
        BioshipStructure.cells(core.ship()).values().stream()
            .mapToInt(
                state ->
                    BlockEnergyTable.units(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()))
            .sum();
    CACHE.put(core, new Snapshot(now, units));
    return units;
  }
}
