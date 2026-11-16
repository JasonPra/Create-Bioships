package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Persistent room capacity and execution of one fully funded atomic expansion. */
final class BioshipEngineRoom {
  private int columns = 1;

  int columns() {
    return columns;
  }

  int capacity() {
    return EngineRoomSizing.capacity(columns);
  }

  int widening() {
    return EngineRoomSizing.widening(columns);
  }

  void restore(int value) {
    columns = Math.clamp(value, 1, 11);
  }

  BioshipEngineRoomPlanner.Quote quote(LivingCoreBlockEntity core) {
    return BioshipEngineRoomPlanner.quote(core);
  }

  boolean expand(LivingCoreBlockEntity core) {
    var quote = quote(core);
    if (core.body().dead()
        || BioshipNutrition.critical(core)
        || quote == null
        || quote.legUpgrades() > 0
        || !EmergencyReserve.after(core, quote.completeCost(core))) return false;
    var account = BioshipStorage.account(core);
    if (!quote.cost().pay(account)) return false;
    var bodyBefore = BioshipBody.actual(core);
    var originals = new LinkedHashMap<BlockPos, BlockState>();
    for (var entry : quote.changes().entrySet()) {
      originals.put(entry.getKey(), core.getLevel().getBlockState(entry.getKey()));
      if (!core.getLevel().setBlock(entry.getKey(), entry.getValue(), 3)) {
        originals.forEach((p, state) -> core.getLevel().setBlock(p, state, 3));
        quote.cost().refund(account);
        return false;
      }
    }
    columns = quote.columns();
    core.blueprint().engineWidth(EngineRoomSizing.width(columns));
    core.body().reconcile(core, bodyBefore);
    core.setChanged();
    return true;
  }
}
