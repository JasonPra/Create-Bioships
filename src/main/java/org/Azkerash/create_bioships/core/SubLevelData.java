package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;

/**
 * Saved variable definitions and defaults. Gameplay systems supply changes, never duplicate them.
 */
public final class SubLevelData {
  public static final String EFFECTS = "create_bioships:effects";
  public static final String HEALTH = "create_bioships:health", STATE = "create_bioships:state";
  public static final int MAX_HUNGER = 20, DEFAULT_HUNGER = 20, MAX_TRUST = 100, DEFAULT_TRUST = 0;

  public record Health(
      double current, double maximum, double damage, boolean dead, String causeOfDeath) {}

  private SubLevelData() {}

  public static void initialize(ServerSubLevel body) {
    var existing = body.getUserDataTag();
    if (existing != null
        && existing.contains(HEALTH)
        && existing.getCompound(STATE).getInt("version") == 1) return;
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
    if (!state.contains("trust")) {
      state.putInt("trust", DEFAULT_TRUST);
      changed = true;
    }
    if (state.getInt("version") != 1) {
      state.putInt("version", 1);
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

  public static int trust(ServerSubLevel body) {
    initialize(body);
    return body.getUserDataTag().getCompound(STATE).getInt("trust");
  }

  public static void hunger(ServerSubLevel body, int value) {
    variable(body, "hunger", Math.clamp(value, 0, MAX_HUNGER));
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
