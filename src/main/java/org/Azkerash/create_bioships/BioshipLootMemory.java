package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** Known kill drops outlive visual memory; unloaded drops retain a navigable last position. */
final class BioshipLootMemory {
  record Target(UUID id, Vec3 position, ItemEntity item) {}

  private record Known(Vec3 position, long at) {}

  private final Map<UUID, Known> drops = new LinkedHashMap<>();

  void remember(ItemEntity item, long at) {
    drops.put(item.getUUID(), new Known(item.position(), at));
    while (drops.size() > 128) drops.remove(drops.keySet().iterator().next());
  }

  boolean knows(Vec3 point) {
    return point != null
        && drops.values().stream().anyMatch(k -> k.position.distanceToSqr(point) < 1);
  }

  List<Target> targets(LivingCoreBlockEntity core) {
    if (!(core.getLevel() instanceof ServerLevel level)) return List.of();
    var result = new ArrayList<Target>();
    var iterator = drops.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      var known = entry.getValue();
      var entity = level.getEntity(entry.getKey());
      if (entity instanceof ItemEntity item
          && item.isAlive()
          && LivingCoreBlockEntity.droppedSupply(item.getItem())) {
        entry.setValue(new Known(item.position(), known.at));
        result.add(new Target(entry.getKey(), item.position(), item));
      } else {
        var pos = BlockPos.containing(known.position);
        boolean loaded = level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (loaded && level.getGameTime() - known.at > 20) iterator.remove();
        else result.add(new Target(entry.getKey(), known.position, null));
      }
    }
    return result;
  }

  CompoundTag save() {
    var data = new CompoundTag();
    var list = new ListTag();
    drops.forEach(
        (id, known) -> {
          var tag = new CompoundTag();
          tag.putUUID("Id", id);
          tag.put("Position", VisionDebug.vector(known.position));
          tag.putLong("At", known.at);
          list.add(tag);
        });
    data.put("Drops", list);
    return data;
  }

  void load(CompoundTag data) {
    drops.clear();
    var list = data.getList("Drops", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(128, list.size()); i++) {
      var tag = list.getCompound(i);
      if (tag.hasUUID("Id"))
        drops.put(
            tag.getUUID("Id"),
            new Known(VisionDebug.vector(tag.getCompound("Position")), tag.getLong("At")));
    }
  }
}
