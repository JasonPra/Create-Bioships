package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Private construction stock in connected native sacs; food slots remain player-accessible. */
final class BioshipStores {
  static final String MATERIALS = "BioshipMaterials", AUTOMATIC = "BioshipAutomaticSac";
  static final int STOMACH_CAPACITY = 128, SAC_CAPACITY = 256;

  static LivingCoreBlockEntity core(StomachBlockEntity stomach) {
    var ship = BioshipIntegration.containing(stomach.getLevel(), stomach.getBlockPos());
    if (ship == null) return null;
    for (var be : BioshipIntegration.blockEntities(ship))
      if (be instanceof LivingCoreBlockEntity c && !c.body().dead()) return c;
    return null;
  }

  static List<BlockEntity> connected(StomachBlockEntity stomach) {
    var core = core(stomach);
    if (core == null) return List.of();
    var result = new ArrayList<BlockEntity>();
    for (var pos : stomach.sacPaths.keySet()) {
      var be = stomach.getLevel().getBlockEntity(pos);
      if (be != null
          && EngineRoom.storage(be)
          && DigestiveSystem.storeConnected(core, stomach, pos)) result.add(be);
    }
    return result;
  }

  static int stored(BlockEntity sac, String id) {
    return sac.getPersistentData().getCompound(MATERIALS).getInt(id);
  }

  static int total(BlockEntity sac) {
    var tag = sac.getPersistentData().getCompound(MATERIALS);
    int total = 0;
    for (var id : tag.getAllKeys()) total += tag.getInt(id);
    return total;
  }

  static int material(StomachBlockEntity stomach, String id) {
    int amount = 0;
    for (var sac : connected(stomach)) amount += stored(sac, id);
    return amount;
  }

  static void change(BlockEntity sac, String id, int delta) {
    var tag = sac.getPersistentData().getCompound(MATERIALS).copy();
    int value = tag.getInt(id) + delta;
    if (value <= 0) tag.remove(id);
    else tag.putInt(id, value);
    sac.getPersistentData().put(MATERIALS, tag);
    sac.setChanged();
  }

  static boolean spend(StomachBlockEntity stomach, String id, int count) {
    var sacs = connected(stomach);
    int available = 0;
    for (var sac : sacs) available += stored(sac, id);
    if (available < count) return false;
    for (var sac : sacs) {
      int take = Math.min(count, stored(sac, id));
      change(sac, id, -take);
      count -= take;
      if (count == 0) break;
    }
    return true;
  }

  static int total(StomachBlockEntity stomach) {
    int total = stomach.localMaterialTotal();
    for (var sac : connected(stomach)) total += total(sac);
    return total;
  }

  static int capacity(StomachBlockEntity stomach) {
    return STOMACH_CAPACITY + SAC_CAPACITY * connected(stomach).size();
  }

  static void balance(StomachBlockEntity stomach) {
    int excess = stomach.localMaterialTotal() - STOMACH_CAPACITY;
    if (excess <= 0) return;
    for (var sac : connected(stomach)) {
      int room = SAC_CAPACITY - total(sac);
      if (room <= 0) continue;
      for (var id : stomach.localMaterialIds()) {
        int take = Math.min(excess, Math.min(room, stomach.localMaterial(id)));
        if (take > 0 && stomach.spendLocal(id, take)) {
          change(sac, id, take);
          room -= take;
          excess -= take;
        }
        if (excess == 0 || room == 0) break;
      }
      if (excess == 0) break;
    }
  }
}
