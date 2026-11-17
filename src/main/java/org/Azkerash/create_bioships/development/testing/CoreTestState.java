package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent fixture state and exact Sable identities, independent of rendering/geometry. */
public final class CoreTestState extends SavedData {
  boolean built, running, combatBuilt, combatRunning;
  BlockPos combatOrigin = new BlockPos(48, -63, 0);
  int combatIndex;
  long combatDeadline;
  UUID combatBody;
  final List<String> combatEnemies = new ArrayList<>(), combatResults = new ArrayList<>();
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
                  state.combatBuilt = tag.getBoolean("CombatBuilt");
                  state.combatRunning = tag.getBoolean("CombatRunning");
                  state.combatIndex = tag.getInt("CombatIndex");
                  state.combatDeadline = tag.getLong("CombatDeadline");
                  if (tag.contains("CombatOrigin"))
                    state.combatOrigin = BlockPos.of(tag.getLong("CombatOrigin"));
                  if (tag.hasUUID("CombatBody")) state.combatBody = tag.getUUID("CombatBody");
                  tag.getList("CombatEnemies", 8)
                      .forEach(e -> state.combatEnemies.add(e.getAsString()));
                  tag.getList("CombatResults", 8)
                      .forEach(e -> state.combatResults.add(e.getAsString()));
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
    tag.putBoolean("CombatBuilt", combatBuilt);
    tag.putBoolean("CombatRunning", combatRunning);
    tag.putLong("CombatOrigin", combatOrigin.asLong());
    tag.putInt("CombatIndex", combatIndex);
    tag.putLong("CombatDeadline", combatDeadline);
    if (combatBody != null) tag.putUUID("CombatBody", combatBody);
    var enemies = new ListTag();
    combatEnemies.forEach(e -> enemies.add(StringTag.valueOf(e)));
    tag.put("CombatEnemies", enemies);
    var results = new ListTag();
    combatResults.forEach(e -> results.add(StringTag.valueOf(e)));
    tag.put("CombatResults", results);
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
