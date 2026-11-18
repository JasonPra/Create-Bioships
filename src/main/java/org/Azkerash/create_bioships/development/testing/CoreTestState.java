package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent fixture state and exact Sable identities, independent of rendering/geometry. */
public final class CoreTestState extends SavedData {
  boolean built, running, combatBuilt, combatRunning;
  boolean visionBuilt, membraneBuilt;
  UUID developmentOwner;
  BlockPos combatOrigin = new BlockPos(1024, DevelopmentWorldSetup.DEFAULT_TEST_Y, 1024);
  boolean combatFailuresExpanded;
  int combatFailurePage;
  int combatIndex;
  long combatDeadline;
  int combatTrialSeconds = 10, combatActiveTrialSeconds = 10;
  UUID combatBody, combatEnemy;
  boolean combatAcquired;
  boolean potionBuilt, potionRunning, potionResistanceHit;
  BlockPos potionOrigin = new BlockPos(256, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  UUID potionBody, potionZombie;
  int potionResistancePhase;
  double potionBaselineDamage, potionProtectedDamage, potionHitBefore;
  int potionIndex;
  long potionDeadline;
  double potionBefore;
  final List<String> potionResults = new ArrayList<>();
  boolean digestionBuilt, digestionRunning;
  BlockPos digestionOrigin = new BlockPos(384, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  UUID digestionBody, digestionControl;
  long digestionDeadline;
  String digestionResult = "Ready";
  boolean arrowBuilt, arrowRunning;
  BlockPos arrowOrigin = new BlockPos(128, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  UUID arrowBody;
  int arrowPhase;
  long arrowDeadline, arrowShotAt, arrowNextAction;
  String arrowResult = "Ready";
  final List<String> combatEnemies = new ArrayList<>(), combatResults = new ArrayList<>();
  BlockPos origin = new BlockPos(0, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
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
                  if (tag.hasUUID("DevelopmentOwner"))
                    state.developmentOwner = tag.getUUID("DevelopmentOwner");
                  state.visionBuilt = tag.getBoolean("VisionBuilt");
                  state.membraneBuilt = tag.getBoolean("MembraneBuilt");
                  state.combatFailuresExpanded = tag.getBoolean("CombatFailuresExpanded");
                  state.combatFailurePage = tag.getInt("CombatFailurePage");
                  state.potionBuilt = tag.getBoolean("PotionBuilt");
                  state.potionRunning = tag.getBoolean("PotionRunning");
                  state.potionResistanceHit = tag.getBoolean("PotionResistanceHit");
                  state.potionIndex = tag.getInt("PotionIndex");
                  state.potionDeadline = tag.getLong("PotionDeadline");
                  state.potionBefore = tag.getDouble("PotionBefore");
                  state.potionResistancePhase = tag.getInt("PotionResistancePhase");
                  state.potionBaselineDamage = tag.getDouble("PotionBaselineDamage");
                  state.potionProtectedDamage = tag.getDouble("PotionProtectedDamage");
                  state.potionHitBefore = tag.getDouble("PotionHitBefore");
                  if (tag.hasUUID("PotionZombie")) state.potionZombie = tag.getUUID("PotionZombie");
                  if (tag.contains("PotionOrigin"))
                    state.potionOrigin = BlockPos.of(tag.getLong("PotionOrigin"));
                  if (tag.hasUUID("PotionBody")) state.potionBody = tag.getUUID("PotionBody");
                  tag.getList("PotionResults", 8)
                      .forEach(e -> state.potionResults.add(e.getAsString()));
                  state.digestionBuilt = tag.getBoolean("DigestionBuilt");
                  state.digestionRunning = tag.getBoolean("DigestionRunning");
                  if (tag.contains("DigestionOrigin"))
                    state.digestionOrigin = BlockPos.of(tag.getLong("DigestionOrigin"));
                  if (tag.hasUUID("DigestionControl"))
                    state.digestionControl = tag.getUUID("DigestionControl");
                  if (tag.hasUUID("DigestionBody"))
                    state.digestionBody = tag.getUUID("DigestionBody");
                  state.digestionDeadline = tag.getLong("DigestionDeadline");
                  if (tag.contains("DigestionResult"))
                    state.digestionResult = tag.getString("DigestionResult");
                  state.arrowBuilt = tag.getBoolean("ArrowBuilt");
                  state.arrowRunning = tag.getBoolean("ArrowRunning");
                  state.arrowPhase = tag.getInt("ArrowPhase");
                  state.arrowDeadline = tag.getLong("ArrowDeadline");
                  state.arrowShotAt = tag.getLong("ArrowShotAt");
                  state.arrowNextAction = tag.getLong("ArrowNextAction");
                  if (tag.contains("ArrowResult")) state.arrowResult = tag.getString("ArrowResult");
                  if (tag.contains("ArrowOrigin"))
                    state.arrowOrigin = BlockPos.of(tag.getLong("ArrowOrigin"));
                  if (tag.hasUUID("ArrowBody")) state.arrowBody = tag.getUUID("ArrowBody");
                  state.combatBuilt = tag.getBoolean("CombatBuilt");
                  state.combatRunning = tag.getBoolean("CombatRunning");
                  state.combatIndex = tag.getInt("CombatIndex");
                  state.combatDeadline = tag.getLong("CombatDeadline");
                  if (tag.contains("CombatTrialSeconds"))
                    state.combatTrialSeconds = Math.clamp(tag.getInt("CombatTrialSeconds"), 5, 120);
                  if (tag.contains("CombatActiveTrialSeconds"))
                    state.combatActiveTrialSeconds =
                        Math.clamp(tag.getInt("CombatActiveTrialSeconds"), 5, 120);
                  if (tag.contains("CombatOrigin"))
                    state.combatOrigin = BlockPos.of(tag.getLong("CombatOrigin"));
                  state.combatAcquired = tag.getBoolean("CombatAcquired");
                  if (tag.hasUUID("CombatEnemy")) state.combatEnemy = tag.getUUID("CombatEnemy");
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
    if (developmentOwner != null) tag.putUUID("DevelopmentOwner", developmentOwner);
    tag.putBoolean("VisionBuilt", visionBuilt);
    tag.putBoolean("MembraneBuilt", membraneBuilt);
    tag.putBoolean("CombatFailuresExpanded", combatFailuresExpanded);
    tag.putInt("CombatFailurePage", combatFailurePage);
    tag.putBoolean("PotionBuilt", potionBuilt);
    tag.putBoolean("PotionRunning", potionRunning);
    tag.putBoolean("PotionResistanceHit", potionResistanceHit);
    tag.putLong("PotionOrigin", potionOrigin.asLong());
    if (potionBody != null) tag.putUUID("PotionBody", potionBody);
    tag.putInt("PotionIndex", potionIndex);
    tag.putLong("PotionDeadline", potionDeadline);
    tag.putDouble("PotionBefore", potionBefore);
    tag.putInt("PotionResistancePhase", potionResistancePhase);
    tag.putDouble("PotionBaselineDamage", potionBaselineDamage);
    tag.putDouble("PotionProtectedDamage", potionProtectedDamage);
    tag.putDouble("PotionHitBefore", potionHitBefore);
    if (potionZombie != null) tag.putUUID("PotionZombie", potionZombie);
    var potionEntries = new ListTag();
    potionResults.forEach(e -> potionEntries.add(StringTag.valueOf(e)));
    tag.put("PotionResults", potionEntries);
    tag.putBoolean("DigestionBuilt", digestionBuilt);
    tag.putBoolean("DigestionRunning", digestionRunning);
    tag.putLong("DigestionOrigin", digestionOrigin.asLong());
    if (digestionBody != null) tag.putUUID("DigestionBody", digestionBody);
    if (digestionControl != null) tag.putUUID("DigestionControl", digestionControl);
    tag.putLong("DigestionDeadline", digestionDeadline);
    tag.putString("DigestionResult", digestionResult);
    tag.putBoolean("ArrowBuilt", arrowBuilt);
    tag.putBoolean("ArrowRunning", arrowRunning);
    tag.putInt("ArrowPhase", arrowPhase);
    tag.putLong("ArrowDeadline", arrowDeadline);
    tag.putLong("ArrowShotAt", arrowShotAt);
    tag.putLong("ArrowNextAction", arrowNextAction);
    tag.putString("ArrowResult", arrowResult);
    tag.putLong("ArrowOrigin", arrowOrigin.asLong());
    if (arrowBody != null) tag.putUUID("ArrowBody", arrowBody);
    tag.putBoolean("CombatBuilt", combatBuilt);
    tag.putBoolean("CombatRunning", combatRunning);
    tag.putLong("CombatOrigin", combatOrigin.asLong());
    tag.putInt("CombatIndex", combatIndex);
    tag.putLong("CombatDeadline", combatDeadline);
    tag.putInt("CombatTrialSeconds", combatTrialSeconds);
    tag.putInt("CombatActiveTrialSeconds", combatActiveTrialSeconds);
    tag.putBoolean("CombatAcquired", combatAcquired);
    if (combatEnemy != null) tag.putUUID("CombatEnemy", combatEnemy);
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
