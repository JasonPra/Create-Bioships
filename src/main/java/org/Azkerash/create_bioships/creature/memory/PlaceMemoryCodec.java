package org.Azkerash.create_bioships.creature.memory;

import java.util.*;
import net.minecraft.nbt.*;

/**
 * Minecraft persistence adapter; inventories and creature controllers are not part of the format.
 */
public final class PlaceMemoryCodec {
  public static CompoundTag save(PlaceMemory memory) {
    var tag = new CompoundTag();
    var entries = new ListTag();
    for (var place : memory.snapshot()) {
      var entry = new CompoundTag();
      entry.putString("Dimension", place.dimension());
      entry.putString("Biome", place.biome());
      entry.putDouble("X", place.point().x());
      entry.putDouble("Y", place.point().y());
      entry.putDouble("Z", place.point().z());
      entry.putLong("TerrainSeen", place.terrainSeen());
      entry.putLong("Updated", place.updatedAt());
      var features = new ListTag();
      place.features().stream().sorted().forEach(f -> features.add(StringTag.valueOf(f.name())));
      entry.put("Features", features);
      entry.put("Prey", evidence(place.prey()));
      entry.put("Danger", evidence(place.danger()));
      entries.add(entry);
    }
    tag.put("Places", entries);
    return tag;
  }

  private static ListTag evidence(Map<String, Long> sightings) {
    var list = new ListTag();
    sightings.forEach(
        (id, seen) -> {
          var tag = new CompoundTag();
          tag.putString("Id", id);
          tag.putLong("Seen", seen);
          list.add(tag);
        });
    return list;
  }

  private static Map<String, Long> evidence(ListTag list, int limit) {
    var result = new HashMap<String, Long>();
    for (int i = 0; i < Math.min(limit, list.size()); i++) {
      var tag = list.getCompound(i);
      result.put(tag.getString("Id"), tag.getLong("Seen"));
    }
    return result;
  }

  public static void load(PlaceMemory memory, CompoundTag tag) {
    var result = new ArrayList<PlaceMemory.Entry>();
    var entries = tag.getList("Places", 10);
    for (int i = 0; i < Math.min(entries.size(), PlaceMemory.DEFAULT_CAPACITY); i++) {
      try {
        var entry = entries.getCompound(i);
        var features = EnumSet.noneOf(PlaceMemory.Feature.class);
        var saved = entry.getList("Features", 8);
        for (int n = 0; n < Math.min(saved.size(), PlaceMemory.Feature.values().length); n++) {
          try {
            features.add(PlaceMemory.Feature.valueOf(saved.getString(n)));
          } catch (IllegalArgumentException ignored) {
          }
        }
        result.add(
            new PlaceMemory.Entry(
                entry.getString("Dimension"),
                new PlaceMemory.Point(
                    entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                entry.getString("Biome"),
                features,
                entry.getLong("TerrainSeen"),
                entry.getLong("Updated"),
                evidence(entry.getList("Prey", 10), 16),
                evidence(entry.getList("Danger", 10), 8)));
      } catch (IllegalArgumentException ignored) {
      } // Discard a malformed record, not the creature.
    }
    memory.restore(result);
  }

  private PlaceMemoryCodec() {}
}
