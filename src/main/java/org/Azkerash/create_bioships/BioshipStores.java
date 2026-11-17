package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.Azkerash.create_bioships.integration.biomancy.NativeStorage;

/** Private construction stock in connected native sacs; food slots remain player-accessible. */
final class BioshipStores {
  static final String MATERIALS = "BioshipMaterials", AUTOMATIC = "BioshipAutomaticSac";
  static final int STOMACH_CAPACITY = 128, SAC_CAPACITY = 256;

  static LivingCoreBlockEntity core(StomachBlockEntity stomach) {
    if (stomach.getLevel() == null) return null;
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
          && NativeStorage.isStorage(be)
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

  static void change(BlockEntity sac, String id, int delta) {
    var tag = sac.getPersistentData().getCompound(MATERIALS).copy();
    int value = tag.getInt(id) + delta;
    if (value <= 0) tag.remove(id);
    else tag.putInt(id, value);
    sac.getPersistentData().put(MATERIALS, tag);
    sac.setChanged();
  }

  static int total(StomachBlockEntity stomach) {
    var core = core(stomach);
    return core == null ? stomach.localMaterialTotal() : BioshipStorage.products(core);
  }

  static int capacity(StomachBlockEntity stomach) {
    var core = core(stomach);
    return core == null ? STOMACH_CAPACITY : BioshipStorage.capacity(core);
  }

  static void balance(StomachBlockEntity stomach) {
    var core = core(stomach);
    if (core != null) BioshipStorage.flush(core);
  }
}
