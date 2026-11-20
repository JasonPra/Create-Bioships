package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent fixture state and exact Sable identities, independent of rendering/geometry. Mutators
 * do not flush partial transitions; callers must mark SavedData dirty after a complete update.
 */
public final class CoreTestState extends SavedData {
  private boolean built, running, combatBuilt, combatRunning;
  private boolean visionBuilt, membraneBuilt, brainBuilt, locomotionBuilt, modularLimbsBuilt;
  private int legChamberVersion, denAccessVersion, denFurnitureVersion, denLandscapeVersion;
  private boolean waterloggingBuilt, aquariumBuilt, juniorHabitatBuilt, necromancerDenBuilt;
  private BlockPos juniorHabitatOrigin = BlockPos.ZERO;
  private int waterloggingCatalogSize;
  private UUID developmentOwner, juniorSubject;
  private long juniorFeedAt;
  private net.minecraft.nbt.CompoundTag juniorBaseline = new net.minecraft.nbt.CompoundTag();
  private BlockPos combatOrigin = new BlockPos(1024, DevelopmentWorldSetup.DEFAULT_TEST_Y, 1024);
  private boolean combatFailuresExpanded;
  private int combatFailurePage;
  private int combatIndex;
  private long combatDeadline;
  private int combatTrialSeconds = 10, combatActiveTrialSeconds = 10;
  private UUID combatBody, combatEnemy;
  private boolean combatAcquired;
  private boolean potionBuilt, potionRunning, potionResistanceHit;
  private BlockPos potionOrigin = new BlockPos(256, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  private UUID potionBody, potionZombie;
  private int potionResistancePhase;
  private double potionBaselineDamage, potionProtectedDamage, potionHitBefore;
  private int potionIndex;
  private long potionDeadline;
  private double potionBefore;
  private final List<String> potionResults = new ArrayList<>();
  private boolean digestionBuilt, digestionRunning;
  private BlockPos digestionOrigin = new BlockPos(384, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  private UUID digestionBody, digestionControl;
  private long digestionDeadline;
  private String digestionResult = "Ready";
  private boolean arrowBuilt, arrowRunning;
  private BlockPos arrowOrigin = new BlockPos(128, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  private UUID arrowBody;
  private int arrowPhase;
  private long arrowDeadline, arrowShotAt, arrowNextAction;
  private String arrowResult = "Ready";
  private final List<String> combatEnemies = new ArrayList<>(), combatResults = new ArrayList<>();
  private BlockPos origin = new BlockPos(0, DevelopmentWorldSetup.DEFAULT_TEST_Y, 0);
  private final Set<UUID> owned = new HashSet<>();

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
                  if (tag.hasUUID("JuniorSubject")) {
                    state.juniorSubject = tag.getUUID("JuniorSubject");
                  }
                  state.juniorFeedAt = tag.getLong("JuniorFeedAt");
                  state.juniorBaseline = tag.getCompound("JuniorBaseline").copy();
                  if (tag.hasUUID("DevelopmentOwner")) {
                    state.developmentOwner = tag.getUUID("DevelopmentOwner");
                  }
                  state.visionBuilt = tag.getBoolean("VisionBuilt");
                  state.membraneBuilt = tag.getBoolean("MembraneBuilt");
                  state.brainBuilt = tag.getBoolean("BrainBuilt");
                  state.waterloggingBuilt = tag.getBoolean("WaterloggingBuilt");
                  state.aquariumBuilt = tag.getBoolean("AquariumBuilt");
                  state.juniorHabitatBuilt = tag.getBoolean("JuniorHabitatBuilt");
                  state.necromancerDenBuilt = tag.getBoolean("NecromancerDenBuilt");
                  state.denAccessVersion = tag.getInt("DenAccessVersion");
                  state.denFurnitureVersion = tag.getInt("DenFurnitureVersion");
                  state.denLandscapeVersion = tag.getInt("DenLandscapeVersion");
                  state.juniorHabitatOrigin = BlockPos.of(tag.getLong("JuniorHabitatOrigin"));
                  state.waterloggingCatalogSize = tag.getInt("WaterloggingCatalogSize");
                  state.legChamberVersion = tag.getInt("LegChamberVersion");
                  state.locomotionBuilt = tag.getBoolean("LocomotionBuilt");
                  state.modularLimbsBuilt = tag.getBoolean("ModularLimbsBuilt");
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
                  if (tag.hasUUID("PotionZombie")) {
                    state.potionZombie = tag.getUUID("PotionZombie");
                  }
                  if (tag.contains("PotionOrigin")) {
                    state.potionOrigin = BlockPos.of(tag.getLong("PotionOrigin"));
                  }
                  if (tag.hasUUID("PotionBody")) {
                    state.potionBody = tag.getUUID("PotionBody");
                  }
                  tag.getList("PotionResults", 8)
                      .forEach(e -> state.potionResults.add(e.getAsString()));
                  state.digestionBuilt = tag.getBoolean("DigestionBuilt");
                  state.digestionRunning = tag.getBoolean("DigestionRunning");
                  if (tag.contains("DigestionOrigin")) {
                    state.digestionOrigin = BlockPos.of(tag.getLong("DigestionOrigin"));
                  }
                  if (tag.hasUUID("DigestionControl")) {
                    state.digestionControl = tag.getUUID("DigestionControl");
                  }
                  if (tag.hasUUID("DigestionBody")) {
                    state.digestionBody = tag.getUUID("DigestionBody");
                  }
                  state.digestionDeadline = tag.getLong("DigestionDeadline");
                  if (tag.contains("DigestionResult")) {
                    state.digestionResult = tag.getString("DigestionResult");
                  }
                  state.arrowBuilt = tag.getBoolean("ArrowBuilt");
                  state.arrowRunning = tag.getBoolean("ArrowRunning");
                  state.arrowPhase = tag.getInt("ArrowPhase");
                  state.arrowDeadline = tag.getLong("ArrowDeadline");
                  state.arrowShotAt = tag.getLong("ArrowShotAt");
                  state.arrowNextAction = tag.getLong("ArrowNextAction");
                  if (tag.contains("ArrowResult")) {
                    state.arrowResult = tag.getString("ArrowResult");
                  }
                  if (tag.contains("ArrowOrigin")) {
                    state.arrowOrigin = BlockPos.of(tag.getLong("ArrowOrigin"));
                  }
                  if (tag.hasUUID("ArrowBody")) {
                    state.arrowBody = tag.getUUID("ArrowBody");
                  }
                  state.combatBuilt = tag.getBoolean("CombatBuilt");
                  state.combatRunning = tag.getBoolean("CombatRunning");
                  state.combatIndex = tag.getInt("CombatIndex");
                  state.combatDeadline = tag.getLong("CombatDeadline");
                  if (tag.contains("CombatTrialSeconds")) {
                    state.combatTrialSeconds = Math.clamp(tag.getInt("CombatTrialSeconds"), 5, 120);
                  }
                  if (tag.contains("CombatActiveTrialSeconds")) {
                    state.combatActiveTrialSeconds =
                        Math.clamp(tag.getInt("CombatActiveTrialSeconds"), 5, 120);
                  }
                  if (tag.contains("CombatOrigin")) {
                    state.combatOrigin = BlockPos.of(tag.getLong("CombatOrigin"));
                  }
                  state.combatAcquired = tag.getBoolean("CombatAcquired");
                  if (tag.hasUUID("CombatEnemy")) {
                    state.combatEnemy = tag.getUUID("CombatEnemy");
                  }
                  if (tag.hasUUID("CombatBody")) {
                    state.combatBody = tag.getUUID("CombatBody");
                  }
                  tag.getList("CombatEnemies", 8)
                      .forEach(e -> state.combatEnemies.add(e.getAsString()));
                  tag.getList("CombatResults", 8)
                      .forEach(e -> state.combatResults.add(e.getAsString()));
                  state.built = tag.getBoolean("Built");
                  state.running = tag.getBoolean("Running");
                  if (tag.contains("Origin")) {
                    state.origin = BlockPos.of(tag.getLong("Origin"));
                  }
                  for (var entry : tag.getList("Owned", 10)) {
                    state.owned.add(((CompoundTag) entry).getUUID("Id"));
                  }
                  return state;
                }),
            "create_bioships_core_regression");
  }

  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    if (developmentOwner != null) {
      tag.putUUID("DevelopmentOwner", developmentOwner);
    }
    if (juniorSubject != null) {
      tag.putUUID("JuniorSubject", juniorSubject);
    }
    tag.putLong("JuniorFeedAt", juniorFeedAt);
    tag.put("JuniorBaseline", juniorBaseline.copy());
    tag.putBoolean("VisionBuilt", visionBuilt);
    tag.putBoolean("MembraneBuilt", membraneBuilt);
    tag.putBoolean("BrainBuilt", brainBuilt);
    tag.putBoolean("WaterloggingBuilt", waterloggingBuilt);
    tag.putBoolean("AquariumBuilt", aquariumBuilt);
    tag.putBoolean("JuniorHabitatBuilt", juniorHabitatBuilt);
    tag.putBoolean("NecromancerDenBuilt", necromancerDenBuilt);
    tag.putInt("DenAccessVersion", denAccessVersion);
    tag.putInt("DenFurnitureVersion", denFurnitureVersion);
    tag.putInt("DenLandscapeVersion", denLandscapeVersion);
    tag.putLong("JuniorHabitatOrigin", juniorHabitatOrigin.asLong());
    tag.putInt("WaterloggingCatalogSize", waterloggingCatalogSize);
    tag.putInt("LegChamberVersion", legChamberVersion);
    tag.putBoolean("LocomotionBuilt", locomotionBuilt);
    tag.putBoolean("ModularLimbsBuilt", modularLimbsBuilt);
    tag.putBoolean("CombatFailuresExpanded", combatFailuresExpanded);
    tag.putInt("CombatFailurePage", combatFailurePage);
    tag.putBoolean("PotionBuilt", potionBuilt);
    tag.putBoolean("PotionRunning", potionRunning);
    tag.putBoolean("PotionResistanceHit", potionResistanceHit);
    tag.putLong("PotionOrigin", potionOrigin.asLong());
    if (potionBody != null) {
      tag.putUUID("PotionBody", potionBody);
    }
    tag.putInt("PotionIndex", potionIndex);
    tag.putLong("PotionDeadline", potionDeadline);
    tag.putDouble("PotionBefore", potionBefore);
    tag.putInt("PotionResistancePhase", potionResistancePhase);
    tag.putDouble("PotionBaselineDamage", potionBaselineDamage);
    tag.putDouble("PotionProtectedDamage", potionProtectedDamage);
    tag.putDouble("PotionHitBefore", potionHitBefore);
    if (potionZombie != null) {
      tag.putUUID("PotionZombie", potionZombie);
    }
    var potionEntries = new ListTag();
    potionResults.forEach(e -> potionEntries.add(StringTag.valueOf(e)));
    tag.put("PotionResults", potionEntries);
    tag.putBoolean("DigestionBuilt", digestionBuilt);
    tag.putBoolean("DigestionRunning", digestionRunning);
    tag.putLong("DigestionOrigin", digestionOrigin.asLong());
    if (digestionBody != null) {
      tag.putUUID("DigestionBody", digestionBody);
    }
    if (digestionControl != null) {
      tag.putUUID("DigestionControl", digestionControl);
    }
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
    if (arrowBody != null) {
      tag.putUUID("ArrowBody", arrowBody);
    }
    tag.putBoolean("CombatBuilt", combatBuilt);
    tag.putBoolean("CombatRunning", combatRunning);
    tag.putLong("CombatOrigin", combatOrigin.asLong());
    tag.putInt("CombatIndex", combatIndex);
    tag.putLong("CombatDeadline", combatDeadline);
    tag.putInt("CombatTrialSeconds", combatTrialSeconds);
    tag.putInt("CombatActiveTrialSeconds", combatActiveTrialSeconds);
    tag.putBoolean("CombatAcquired", combatAcquired);
    if (combatEnemy != null) {
      tag.putUUID("CombatEnemy", combatEnemy);
    }
    if (combatBody != null) {
      tag.putUUID("CombatBody", combatBody);
    }
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

  public boolean isBuilt() {
    return this.built;
  }

  public void setBuilt(boolean value) {
    this.built = value;
  }

  public boolean isRunning() {
    return this.running;
  }

  public void setRunning(boolean value) {
    this.running = value;
  }

  public boolean isCombatBuilt() {
    return this.combatBuilt;
  }

  public void setCombatBuilt(boolean value) {
    this.combatBuilt = value;
  }

  public boolean isCombatRunning() {
    return this.combatRunning;
  }

  public void setCombatRunning(boolean value) {
    this.combatRunning = value;
  }

  public boolean isVisionBuilt() {
    return this.visionBuilt;
  }

  public void setVisionBuilt(boolean value) {
    this.visionBuilt = value;
  }

  public boolean isMembraneBuilt() {
    return this.membraneBuilt;
  }

  public void setMembraneBuilt(boolean value) {
    this.membraneBuilt = value;
  }

  public boolean isBrainBuilt() {
    return this.brainBuilt;
  }

  public void setBrainBuilt(boolean value) {
    this.brainBuilt = value;
  }

  public boolean isLocomotionBuilt() {
    return this.locomotionBuilt;
  }

  public void setLocomotionBuilt(boolean value) {
    this.locomotionBuilt = value;
  }

  public boolean isModularLimbsBuilt() {
    return this.modularLimbsBuilt;
  }

  public void setModularLimbsBuilt(boolean value) {
    this.modularLimbsBuilt = value;
  }

  public int getLegChamberVersion() {
    return this.legChamberVersion;
  }

  public void setLegChamberVersion(int value) {
    this.legChamberVersion = value;
  }

  public int getDenAccessVersion() {
    return this.denAccessVersion;
  }

  public void setDenAccessVersion(int value) {
    this.denAccessVersion = value;
  }

  public int getDenFurnitureVersion() {
    return this.denFurnitureVersion;
  }

  public void setDenFurnitureVersion(int value) {
    this.denFurnitureVersion = value;
  }

  public int getDenLandscapeVersion() {
    return this.denLandscapeVersion;
  }

  public void setDenLandscapeVersion(int value) {
    this.denLandscapeVersion = value;
  }

  public boolean isWaterloggingBuilt() {
    return this.waterloggingBuilt;
  }

  public void setWaterloggingBuilt(boolean value) {
    this.waterloggingBuilt = value;
  }

  public boolean isAquariumBuilt() {
    return this.aquariumBuilt;
  }

  public void setAquariumBuilt(boolean value) {
    this.aquariumBuilt = value;
  }

  public boolean isJuniorHabitatBuilt() {
    return this.juniorHabitatBuilt;
  }

  public void setJuniorHabitatBuilt(boolean value) {
    this.juniorHabitatBuilt = value;
  }

  public boolean isNecromancerDenBuilt() {
    return this.necromancerDenBuilt;
  }

  public void setNecromancerDenBuilt(boolean value) {
    this.necromancerDenBuilt = value;
  }

  public BlockPos getJuniorHabitatOrigin() {
    return this.juniorHabitatOrigin;
  }

  public void setJuniorHabitatOrigin(BlockPos value) {
    this.juniorHabitatOrigin = value;
  }

  public int getWaterloggingCatalogSize() {
    return this.waterloggingCatalogSize;
  }

  public void setWaterloggingCatalogSize(int value) {
    this.waterloggingCatalogSize = value;
  }

  public UUID getDevelopmentOwner() {
    return this.developmentOwner;
  }

  public void setDevelopmentOwner(UUID value) {
    this.developmentOwner = value;
  }

  public UUID getJuniorSubject() {
    return this.juniorSubject;
  }

  public void setJuniorSubject(UUID value) {
    this.juniorSubject = value;
  }

  public long getJuniorFeedAt() {
    return this.juniorFeedAt;
  }

  public void setJuniorFeedAt(long value) {
    this.juniorFeedAt = value;
  }

  public net.minecraft.nbt.CompoundTag getJuniorBaseline() {
    return this.juniorBaseline;
  }

  public void setJuniorBaseline(net.minecraft.nbt.CompoundTag value) {
    this.juniorBaseline = value;
  }

  public BlockPos getCombatOrigin() {
    return this.combatOrigin;
  }

  public void setCombatOrigin(BlockPos value) {
    this.combatOrigin = value;
  }

  public boolean isCombatFailuresExpanded() {
    return this.combatFailuresExpanded;
  }

  public void setCombatFailuresExpanded(boolean value) {
    this.combatFailuresExpanded = value;
  }

  public int getCombatFailurePage() {
    return this.combatFailurePage;
  }

  public void setCombatFailurePage(int value) {
    this.combatFailurePage = value;
  }

  public void incrementCombatFailurePage() {
    this.combatFailurePage++;
  }

  public int getCombatIndex() {
    return this.combatIndex;
  }

  public void setCombatIndex(int value) {
    this.combatIndex = value;
  }

  public void incrementCombatIndex() {
    this.combatIndex++;
  }

  public long getCombatDeadline() {
    return this.combatDeadline;
  }

  public void setCombatDeadline(long value) {
    this.combatDeadline = value;
  }

  public int getCombatTrialSeconds() {
    return this.combatTrialSeconds;
  }

  public void setCombatTrialSeconds(int value) {
    this.combatTrialSeconds = value;
  }

  public int getCombatActiveTrialSeconds() {
    return this.combatActiveTrialSeconds;
  }

  public void setCombatActiveTrialSeconds(int value) {
    this.combatActiveTrialSeconds = value;
  }

  public UUID getCombatBody() {
    return this.combatBody;
  }

  public void setCombatBody(UUID value) {
    this.combatBody = value;
  }

  public UUID getCombatEnemy() {
    return this.combatEnemy;
  }

  public void setCombatEnemy(UUID value) {
    this.combatEnemy = value;
  }

  public boolean isCombatAcquired() {
    return this.combatAcquired;
  }

  public void setCombatAcquired(boolean value) {
    this.combatAcquired = value;
  }

  public boolean isPotionBuilt() {
    return this.potionBuilt;
  }

  public void setPotionBuilt(boolean value) {
    this.potionBuilt = value;
  }

  public boolean isPotionRunning() {
    return this.potionRunning;
  }

  public void setPotionRunning(boolean value) {
    this.potionRunning = value;
  }

  public boolean isPotionResistanceHit() {
    return this.potionResistanceHit;
  }

  public void setPotionResistanceHit(boolean value) {
    this.potionResistanceHit = value;
  }

  public BlockPos getPotionOrigin() {
    return this.potionOrigin;
  }

  public void setPotionOrigin(BlockPos value) {
    this.potionOrigin = value;
  }

  public UUID getPotionBody() {
    return this.potionBody;
  }

  public void setPotionBody(UUID value) {
    this.potionBody = value;
  }

  public UUID getPotionZombie() {
    return this.potionZombie;
  }

  public void setPotionZombie(UUID value) {
    this.potionZombie = value;
  }

  public int getPotionResistancePhase() {
    return this.potionResistancePhase;
  }

  public void setPotionResistancePhase(int value) {
    this.potionResistancePhase = value;
  }

  public double getPotionBaselineDamage() {
    return this.potionBaselineDamage;
  }

  public void setPotionBaselineDamage(double value) {
    this.potionBaselineDamage = value;
  }

  public double getPotionProtectedDamage() {
    return this.potionProtectedDamage;
  }

  public void setPotionProtectedDamage(double value) {
    this.potionProtectedDamage = value;
  }

  public double getPotionHitBefore() {
    return this.potionHitBefore;
  }

  public void setPotionHitBefore(double value) {
    this.potionHitBefore = value;
  }

  public int getPotionIndex() {
    return this.potionIndex;
  }

  public void setPotionIndex(int value) {
    this.potionIndex = value;
  }

  public void incrementPotionIndex() {
    this.potionIndex++;
  }

  public long getPotionDeadline() {
    return this.potionDeadline;
  }

  public void setPotionDeadline(long value) {
    this.potionDeadline = value;
  }

  public double getPotionBefore() {
    return this.potionBefore;
  }

  public void setPotionBefore(double value) {
    this.potionBefore = value;
  }

  public List<String> getPotionResults() {
    return this.potionResults;
  }

  public boolean isDigestionBuilt() {
    return this.digestionBuilt;
  }

  public void setDigestionBuilt(boolean value) {
    this.digestionBuilt = value;
  }

  public boolean isDigestionRunning() {
    return this.digestionRunning;
  }

  public void setDigestionRunning(boolean value) {
    this.digestionRunning = value;
  }

  public BlockPos getDigestionOrigin() {
    return this.digestionOrigin;
  }

  public void setDigestionOrigin(BlockPos value) {
    this.digestionOrigin = value;
  }

  public UUID getDigestionBody() {
    return this.digestionBody;
  }

  public void setDigestionBody(UUID value) {
    this.digestionBody = value;
  }

  public UUID getDigestionControl() {
    return this.digestionControl;
  }

  public void setDigestionControl(UUID value) {
    this.digestionControl = value;
  }

  public long getDigestionDeadline() {
    return this.digestionDeadline;
  }

  public void setDigestionDeadline(long value) {
    this.digestionDeadline = value;
  }

  public String getDigestionResult() {
    return this.digestionResult;
  }

  public void setDigestionResult(String value) {
    this.digestionResult = value;
  }

  public boolean isArrowBuilt() {
    return this.arrowBuilt;
  }

  public void setArrowBuilt(boolean value) {
    this.arrowBuilt = value;
  }

  public boolean isArrowRunning() {
    return this.arrowRunning;
  }

  public void setArrowRunning(boolean value) {
    this.arrowRunning = value;
  }

  public BlockPos getArrowOrigin() {
    return this.arrowOrigin;
  }

  public void setArrowOrigin(BlockPos value) {
    this.arrowOrigin = value;
  }

  public UUID getArrowBody() {
    return this.arrowBody;
  }

  public void setArrowBody(UUID value) {
    this.arrowBody = value;
  }

  public int getArrowPhase() {
    return this.arrowPhase;
  }

  public void setArrowPhase(int value) {
    this.arrowPhase = value;
  }

  public long getArrowDeadline() {
    return this.arrowDeadline;
  }

  public void setArrowDeadline(long value) {
    this.arrowDeadline = value;
  }

  public long getArrowShotAt() {
    return this.arrowShotAt;
  }

  public void setArrowShotAt(long value) {
    this.arrowShotAt = value;
  }

  public long getArrowNextAction() {
    return this.arrowNextAction;
  }

  public void setArrowNextAction(long value) {
    this.arrowNextAction = value;
  }

  public String getArrowResult() {
    return this.arrowResult;
  }

  public void setArrowResult(String value) {
    this.arrowResult = value;
  }

  public List<String> getCombatEnemies() {
    return this.combatEnemies;
  }

  public List<String> getCombatResults() {
    return this.combatResults;
  }

  public BlockPos getOrigin() {
    return this.origin;
  }

  public void setOrigin(BlockPos value) {
    this.origin = value;
  }

  public Set<UUID> getOwned() {
    return this.owned;
  }
}
