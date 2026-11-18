package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;

/**
 * Saved variable definitions and defaults. Gameplay systems supply changes, never duplicate them.
 */
public final class SubLevelData {
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

  public record Hunger(int value, boolean paused, int drainTicks, int starvationTicks) {}

  private SubLevelData() {}

  public static CompoundTag coreSnapshot(ServerSubLevel body) {
    initialize(body);
    var result = new CompoundTag();
    var root = body.getUserDataTag();
    result.put(HEALTH, root.getCompound(HEALTH).copy());
    result.put(STATE, root.getCompound(STATE).copy());
    return result;
  }

  public static void restoreDeadCore(ServerSubLevel body, CompoundTag snapshot) {
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
    var existing = body.getUserDataTag();
    if (existing != null
        && existing.contains(HEALTH)
        && existing.getCompound(STATE).getInt("version") == 3) return;
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
    if (state.getInt("version") != 3) {
      state.putInt("version", 3);
      changed = true;
    }
    if (changed) {
      root.put(HEALTH, health);
      root.put(STATE, state);
      body.setUserDataTag(root);
    }
  }

  public static Health health(ServerSubLevel body) {
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
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getInt("hunger");
  }

  public static int maximumHunger(ServerSubLevel body) {
    SubLevelHealth.status(
        body); // Initializes the event-driven component census once after loading.
    return body.getUserDataTag().getCompound(STATE).getInt("maximumHunger");
  }

  static void maximumHunger(ServerSubLevel body, int capacity) {
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
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getInt("regenerationTicks");
  }

  static void regenerationTicks(ServerSubLevel body, int ticks) {
    variable(body, "regenerationTicks", Math.max(0, ticks));
  }

  public static int trust(ServerSubLevel body) {
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getInt("trust");
  }

  public static void hunger(ServerSubLevel body, int value) {
    var old = hungerState(body);
    hungerState(
        body,
        new Hunger(Math.clamp(value, 0, maximumHunger(body)), old.paused(), old.drainTicks(), 0));
  }

  public static Hunger hungerState(ServerSubLevel body) {
    initialize(body);
    var state = body.getUserDataTag().getCompound(STATE);
    return new Hunger(
        state.getInt("hunger"),
        state.getBoolean("hungerPaused"),
        state.getInt("hungerDrainTicks"),
        state.getInt("starvationTicks"));
  }

  public static void hungerPaused(ServerSubLevel body, boolean paused) {
    var old = hungerState(body);
    if (old.paused() != paused)
      hungerState(body, new Hunger(old.value(), paused, old.drainTicks(), old.starvationTicks()));
  }

  static void hungerState(ServerSubLevel body, Hunger value) {
    initialize(body);
    int maximum = maximumHunger(body);
    var root = body.getUserDataTag().copy();
    var state = root.getCompound(STATE);
    state.putInt("hunger", Math.clamp(value.value(), 0, maximum));
    state.putBoolean("hungerPaused", value.paused());
    state.putInt("hungerDrainTicks", Math.max(0, value.drainTicks()));
    state.putInt("starvationTicks", Math.max(0, value.starvationTicks()));
    body.setUserDataTag(root);
  }

  public static void trust(ServerSubLevel body, int value) {
    variable(body, "trust", Math.clamp(value, 0, MAX_TRUST));
  }

  private static void variable(ServerSubLevel body, String name, int value) {
    initialize(body);
    var root = body.getUserDataTag().copy();
    root.getCompound(STATE).putInt(name, value);
    body.setUserDataTag(root);
  }
}
