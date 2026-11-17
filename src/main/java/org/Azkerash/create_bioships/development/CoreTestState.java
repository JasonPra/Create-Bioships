package org.Azkerash.create_bioships.development;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent fixture state and exact Sable identities, independent of rendering/geometry. */
public final class CoreTestState extends SavedData {
  boolean built, running;
  BlockPos origin = new BlockPos(0, -63, 0);
  final Set<UUID> owned = new HashSet<>();

  public CoreSplitFixture.Layout layout() {
    return new CoreSplitFixture.Layout(origin);
  }

  public boolean running() {
    return running;
  }

  public static CoreTestState get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            new Factory<>(
                CoreTestState::new,
                (tag, registries) -> {
                  var state = new CoreTestState();
                  state.built = tag.getBoolean("Built");
                  state.running = tag.getBoolean("Running");
                  if (tag.contains("Origin")) state.origin = BlockPos.of(tag.getLong("Origin"));
                  for (var entry : tag.getList("Owned", 10))
                    state.owned.add(((CompoundTag) entry).getUUID("Id"));
                  return state;
                }),
            "create_bioships_core_regression");
  }

  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.putBoolean("Built", built);
    tag.putBoolean("Running", running);
    tag.putLong("Origin", origin.asLong());
    var ids = new ListTag();
    for (var id : owned) {
      var entry = new CompoundTag();
      entry.putUUID("Id", id);
      ids.add(entry);
    }
    tag.put("Owned", ids);
    return tag;
  }
}
