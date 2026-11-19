package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;

/**
 * Saved variable definitions and defaults. Gameplay systems supply changes, never duplicate them.
 */
public final class SubLevelData {
  public static final String BEHAVIOR = "create_bioships:behavior";
  public static final String PARTS = "create_bioships:parts";
  public static final String ARMOR = "create_bioships:armor";
  public static final String BRAIN = "create_bioships:brain";
  public static final String ACCESS = "create_bioships:access";
  public static final String LIVING = "create_bioships:living";

  public static boolean isLiving(dev.ryanhcode.sable.sublevel.SubLevel entry) {
    if (!(entry instanceof ServerSubLevel body) || body.getUserDataTag() == null) return false;
    var root = body.getUserDataTag();
    // Migrate the old amoeba identity once; an explicit false flag remains authoritative.
    if (!root.contains(LIVING) && "amoeba".equals(root.getString(CoreAssemblyRules.TYPE))) {
      root = root.copy();
      root.putBoolean(LIVING, true);
      body.setUserDataTag(root);
    }
    return root.getBoolean(LIVING);
  }

  public static void living(ServerSubLevel body, boolean living) {
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    root.putBoolean(LIVING, living);
    body.setUserDataTag(root);
  }

  public static final String EFFECTS = "create_bioships:effects";
  public static final String HEALTH = "create_bioships:health", STATE = "create_bioships:state";
  public static final int BASE_HUNGER = 5,
      STOMACH_HUNGER = 5,
      DEFAULT_HUNGER = 5,
      MAX_TRUST = 100,
      DEFAULT_TRUST = 0;

  public record Health(
      double current, double maximum, double damage, boolean dead, String causeOfDeath) {}

  /** Mass units are Sable's native units (one packed-flesh block is approximately one unit). */
  public static final double HUNGER_REFERENCE_MASS = 10;

  public record Hunger(int value, boolean paused, double drainTicks, int starvationTicks) {}

  private SubLevelData() {}

  public static CompoundTag coreSnapshot(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var result = new CompoundTag();
    var root = body.getUserDataTag();
    result.put(HEALTH, root.getCompound(HEALTH).copy());
    result.put(STATE, root.getCompound(STATE).copy());
    return result;
  }

  public static void restoreDeadCore(ServerSubLevel body, CompoundTag snapshot) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    var health = SubLevelHealth.status(body);
    if (health.dead()) return;
    var old = snapshot.getCompound(HEALTH);
    String cause = old.getString("cause");
    health(
        body,
        new Health(
            0,
            health.maximum(),
            Math.max(health.maximum(), old.getDouble("damage")),
            true,
            cause.isEmpty() ? "detached_core" : cause));
    var saved = snapshot.getCompound(STATE);
    if (!saved.isEmpty()) {
      var root = body.getUserDataTag().copy();
      root.put(STATE, saved.copy());
      body.setUserDataTag(root);
      SubLevelHunger.refreshCapacity(body);
    }
  }

  public static void initialize(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    var existing = body.getUserDataTag();
    if (existing != null
        && existing.contains(HEALTH)
        && existing.getCompound(STATE).getInt("version") == 4) return;
    var root = existing == null ? new CompoundTag() : existing.copy();
    var health = root.getCompound(HEALTH);
    var state = root.getCompound(STATE);
    boolean changed = !root.contains(HEALTH) || !root.contains(STATE);
    if (!health.contains("current")) {
      health.putDouble("current", 0);
      changed = true;
    }
    if (!health.contains("maximum")) {
      health.putDouble("maximum", 0);
      changed = true;
    }
    if (!health.contains("damage")) {
      health.putDouble(
          "damage", Math.max(0, health.getDouble("maximum") - health.getDouble("current")));
      changed = true;
    }
    if (!health.contains("dead")) {
      health.putBoolean("dead", false);
      changed = true;
    }
    if (!health.contains("cause")) {
      health.putString("cause", "");
      changed = true;
    }
    if (!state.contains("hunger")) {
      state.putInt("hunger", DEFAULT_HUNGER);
      changed = true;
    }
    if (!state.contains("maximumHunger")) {
      state.putInt("maximumHunger", BASE_HUNGER);
      changed = true;
    }
    if (state.getInt("hunger") < 0 || state.getInt("hunger") > state.getInt("maximumHunger")) {
      state.putInt("hunger", Math.clamp(state.getInt("hunger"), 0, state.getInt("maximumHunger")));
      changed = true;
    }
    if (!state.contains("hungerPaused")) {
      state.putBoolean("hungerPaused", false);
      changed = true;
    }
    if (!state.contains("hungerDrainTicks")) {
      state.putInt("hungerDrainTicks", 0);
      changed = true;
    }
    if (!state.contains("starvationTicks")) {
      state.putInt("starvationTicks", 0);
      changed = true;
    }
    if (!state.contains("regenerationTicks")) {
      state.putInt("regenerationTicks", 0);
      changed = true;
    }
    if (!state.contains("trust")) {
      state.putInt("trust", DEFAULT_TRUST);
      changed = true;
    }
    for (var key : new String[] {"hasLegs", "hasFins", "hasWallClimbers"})
      if (!state.contains(key)) {
        state.putBoolean(key, false);
        changed = true;
      }
    if (state.getInt("version") != 4) {
      state.putInt("version", 4);
      changed = true;
    }
    if (changed) {
      root.put(HEALTH, health);
      root.put(STATE, state);
      body.setUserDataTag(root);
    }
  }

  public record Locomotion(boolean hasLegs, boolean hasFins, boolean hasWallClimbers) {}

  public static Locomotion locomotion(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var state = body.getUserDataTag().getCompound(STATE);
    return new Locomotion(
        state.getBoolean("hasLegs"),
        state.getBoolean("hasFins"),
        state.getBoolean("hasWallClimbers"));
  }

  public static void locomotion(ServerSubLevel body, Locomotion value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    if (value.equals(locomotion(body))) return;
    var root = body.getUserDataTag().copy();
    var state = root.getCompound(STATE);
    state.putBoolean("hasLegs", value.hasLegs());
    state.putBoolean("hasFins", value.hasFins());
    state.putBoolean("hasWallClimbers", value.hasWallClimbers());
    body.setUserDataTag(root);
  }

  public static Health health(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var value = body.getUserDataTag().getCompound(HEALTH);
    return new Health(
        value.getDouble("current"),
        value.getDouble("maximum"),
        value.getDouble("damage"),
        value.getBoolean("dead"),
        value.getString("cause"));
  }

  static void health(ServerSubLevel body, Health value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var root = body.getUserDataTag().copy();
    var data = new CompoundTag();
    data.putDouble("current", value.current());
    data.putDouble("maximum", value.maximum());
    data.putDouble("damage", value.damage());
    data.putBoolean("dead", value.dead());
    data.putString("cause", value.causeOfDeath());
    root.put(HEALTH, data);
    body.setUserDataTag(root);
  }

  public static int hunger(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getInt("hunger");
  }

  public static int maximumHunger(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    SubLevelHealth.status(
        body); // Initializes the event-driven component census once after loading.
    return body.getUserDataTag().getCompound(STATE).getInt("maximumHunger");
  }

  static void maximumHunger(ServerSubLevel body, int capacity) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var current = body.getUserDataTag().getCompound(STATE);
    if (current.getInt("maximumHunger") == capacity && current.getInt("hunger") <= capacity) return;
    var root = body.getUserDataTag().copy();
    var state = root.getCompound(STATE);
    state.putInt("maximumHunger", capacity);
    state.putInt("hunger", Math.clamp(state.getInt("hunger"), 0, capacity));
    body.setUserDataTag(root);
  }

  public static int regenerationTicks(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getInt("regenerationTicks");
  }

  static void regenerationTicks(ServerSubLevel body, int ticks) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    variable(body, "regenerationTicks", Math.max(0, ticks));
  }

  public static double trust(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getDouble("trust");
  }

  public static void hunger(ServerSubLevel body, int value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    var old = hungerState(body);
    hungerState(
        body,
        new Hunger(Math.clamp(value, 0, maximumHunger(body)), old.paused(), old.drainTicks(), 0));
  }

  public static Hunger hungerState(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var state = body.getUserDataTag().getCompound(STATE);
    return new Hunger(
        state.getInt("hunger"),
        state.getBoolean("hungerPaused"),
        state.getDouble("hungerDrainTicks"),
        state.getInt("starvationTicks"));
  }

  public static void hungerPaused(ServerSubLevel body, boolean paused) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    var old = hungerState(body);
    if (old.paused() != paused)
      hungerState(body, new Hunger(old.value(), paused, old.drainTicks(), old.starvationTicks()));
  }

  static void hungerState(ServerSubLevel body, Hunger value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    int maximum = maximumHunger(body);
    var root = body.getUserDataTag().copy();
    var state = root.getCompound(STATE);
    state.putInt("hunger", Math.clamp(value.value(), 0, maximum));
    state.putBoolean("hungerPaused", value.paused());
    state.putDouble(
        "hungerDrainTicks",
        Double.isFinite(value.drainTicks()) ? Math.max(0, value.drainTicks()) : 0);
    state.putInt("starvationTicks", Math.max(0, value.starvationTicks()));
    body.setUserDataTag(root);
  }

  public static final int TRUST_ATTACK_LOG_LIMIT = 8;

  public record OwnerHit(long tick, double damage, java.util.UUID player) {}

  public static java.util.List<OwnerHit> ownerHits(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var list = body.getUserDataTag().getCompound(STATE).getList("ownerHits", 10);
    var result = new java.util.ArrayList<OwnerHit>();
    for (int i = Math.max(0, list.size() - TRUST_ATTACK_LOG_LIMIT); i < list.size(); i++) {
      var hit = list.getCompound(i);
      double damage = hit.getDouble("damage");
      if (hit.hasUUID("player")
          && hit.getLong("tick") >= 0
          && Double.isFinite(damage)
          && damage >= 0)
        result.add(new OwnerHit(hit.getLong("tick"), damage, hit.getUUID("player")));
    }
    return java.util.List.copyOf(result);
  }

  public static void ownerHits(ServerSubLevel body, java.util.List<OwnerHit> hits) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var root = body.getUserDataTag().copy();
    var list = new net.minecraft.nbt.ListTag();
    for (var hit : hits.subList(Math.max(0, hits.size() - TRUST_ATTACK_LOG_LIMIT), hits.size())) {
      if (hit.player() == null
          || hit.tick() < 0
          || !Double.isFinite(hit.damage())
          || hit.damage() < 0) continue;
      var item = new CompoundTag();
      item.putLong("tick", hit.tick());
      item.putDouble("damage", hit.damage());
      item.putUUID("player", hit.player());
      list.add(item);
    }
    root.getCompound(STATE).put("ownerHits", list);
    body.setUserDataTag(root);
  }

  public record Oxygen(double value, int suffocationTicks, boolean paused) {}

  public static Oxygen oxygen(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var tag = body.getUserDataTag().getCompound(STATE);
    double value = tag.contains("oxygen") ? tag.getDouble("oxygen") : SubLevelOxygen.MAXIMUM;
    return new Oxygen(
        Double.isFinite(value)
            ? Math.clamp(value, 0, SubLevelOxygen.MAXIMUM)
            : SubLevelOxygen.MAXIMUM,
        Math.clamp(tag.getInt("suffocationTicks"), 0, SubLevelOxygen.SUFFOCATION_INTERVAL - 1),
        tag.getBoolean("oxygenPaused"));
  }

  public static void oxygenPaused(ServerSubLevel body, boolean paused) {
    var old = oxygen(body);
    oxygen(body, new Oxygen(old.value(), old.suffocationTicks(), paused));
  }

  public static void oxygen(ServerSubLevel body, Oxygen value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var root = body.getUserDataTag().copy();
    var tag = root.getCompound(STATE);
    tag.putDouble(
        "oxygen",
        Double.isFinite(value.value()) ? Math.clamp(value.value(), 0, SubLevelOxygen.MAXIMUM) : 0);
    tag.putInt(
        "suffocationTicks",
        Math.clamp(value.suffocationTicks(), 0, SubLevelOxygen.SUFFOCATION_INTERVAL - 1));
    tag.putBoolean("oxygenPaused", value.paused());
    body.setUserDataTag(root);
  }

  public record DigestiveSickness(long until, long nextEpisode) {}

  public static DigestiveSickness digestiveSickness(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var state = body.getUserDataTag().getCompound(STATE);
    return new DigestiveSickness(
        state.getLong("digestiveSicknessUntil"), state.getLong("digestiveSicknessNext"));
  }

  public static void digestiveSickness(ServerSubLevel body, DigestiveSickness value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var tag = body.getUserDataTag().copy();
    tag.getCompound(STATE).putLong("digestiveSicknessUntil", Math.max(0, value.until()));
    tag.getCompound(STATE).putLong("digestiveSicknessNext", Math.max(0, value.nextEpisode()));
    body.setUserDataTag(tag);
  }

  public static long regenerationTrustUntil(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getLong("regenerationTrustUntil");
  }

  public static void regenerationTrustUntil(ServerSubLevel body, long until) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var tag = body.getUserDataTag().copy();
    tag.getCompound(STATE).putLong("regenerationTrustUntil", until);
    body.setUserDataTag(tag);
  }

  public static void trust(ServerSubLevel body, double value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    if (!Double.isFinite(value)) return;
    initialize(body);
    var root = body.getUserDataTag().copy();
    root.getCompound(STATE).putDouble("trust", Math.clamp(value, 0, MAX_TRUST));
    body.setUserDataTag(root);
  }

  private static void variable(ServerSubLevel body, String name, int value) {
    if (!SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var root = body.getUserDataTag().copy();
    root.getCompound(STATE).putInt(name, value);
    body.setUserDataTag(root);
  }
}
