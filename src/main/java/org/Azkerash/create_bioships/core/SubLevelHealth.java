package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;

/** The sublevel owns persistent health and death; blocks only contribute capacity. */
public final class SubLevelHealth {
  public static final String DATA = "create_bioships:health";
  private static final Map<ServerSubLevel, Map<Long, Double>> CELLS = new WeakHashMap<>();

  private static final ThreadLocal<Set<ServerSubLevel>> SAFE_REMOVALS =
      ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

  public record Status(double current, double maximum, boolean dead, String causeOfDeath) {}

  private SubLevelHealth() {}

  public static java.util.List<BlockPos> blocks(ServerSubLevel body) {
    initialize(body);
    return CELLS.get(body).keySet().stream().map(BlockPos::of).toList();
  }

  public static Status status(ServerSubLevel body) {
    initialize(body);
    var tag = body.getUserDataTag().getCompound(DATA);
    return new Status(
        tag.getDouble("current"),
        tag.getDouble("maximum"),
        tag.getBoolean("dead"),
        tag.getString("cause"));
  }

  private static void initialize(ServerSubLevel body) {
    if (CELLS.containsKey(body)) return;
    var cells = new HashMap<Long, Double>();
    double maximum = 0;
    for (var holder : body.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int i = 0; i < sections.length; i++) {
        var section = sections[i];
        if (section.hasOnlyAir()) continue;
        int floor = chunk.getSectionYFromSectionIndex(i) << 4;
        for (int x = 0; x < 16; x++)
          for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++) {
              double hp = BlockHealthValues.health(section.getBlockState(x, y, z));
              if (hp == 0) continue;
              cells.put(
                  new BlockPos(
                          chunk.getPos().getMinBlockX() + x,
                          floor + y,
                          chunk.getPos().getMinBlockZ() + z)
                      .asLong(),
                  hp);
              maximum += hp;
            }
      }
    }
    CELLS.put(body, cells);
    var root = body.getUserDataTag();
    var old = root == null ? new CompoundTag() : root.getCompound(DATA);
    store(body, damageFromTag(old), maximum, old.getBoolean("dead"), old.getString("cause"));
  }

  private static double damageFromTag(CompoundTag data) {
    return data.contains("damage")
        ? Math.max(0, data.getDouble("damage"))
        : Math.max(0, data.getDouble("maximum") - data.getDouble("current"));
  }

  private static double damageTaken(ServerSubLevel body) {
    return damageFromTag(body.getUserDataTag().getCompound(DATA));
  }

  /** Create owns pickup/drops; suppress only damage from the resulting capacity change. */
  public static <T> T withoutRemovalDamage(
      ServerSubLevel body, java.util.function.Supplier<T> removal) {
    var before = status(body);
    double beforeDamage = damageTaken(body);
    var active = SAFE_REMOVALS.get();
    boolean added = active.add(body);
    try {
      return removal.get();
    } finally {
      var after = status(body);
      if (added) active.remove(body);
      if (active.isEmpty()) SAFE_REMOVALS.remove();
      store(
          body,
          Math.max(beforeDamage, damageTaken(body)),
          after.maximum(),
          before.dead() || after.dead(),
          after.maximum() < before.maximum() ? "component_loss" : after.causeOfDeath());
    }
  }

  /** Called by Sable after a plot block changes. State-only animations cost no health work. */
  public static void blockChanged(ServerSubLevel body, BlockPos pos, BlockState state) {
    if (!CoreAssemblyRules.isCreature(body) || !CELLS.containsKey(body)) return;
    var cells = CELLS.get(body);
    double previous = cells.getOrDefault(pos.asLong(), 0d), next = BlockHealthValues.health(state);
    if (previous == next) return;
    if (next == 0) cells.remove(pos.asLong());
    else cells.put(pos.asLong(), next);
    var old = status(body);
    double delta = next - previous;
    store(
        body,
        damageTaken(body),
        old.maximum() + delta,
        old.dead(),
        delta < 0 ? "component_loss" : old.causeOfDeath());
  }

  public static boolean damage(ServerSubLevel body, double amount, DamageSource source) {
    return damage(body, amount, source.getMsgId());
  }

  public static boolean damage(ServerSubLevel body, double amount, String cause) {
    if (!Double.isFinite(amount) || amount <= 0) return false;
    var old = status(body);
    if (old.dead()) return false;
    store(body, damageTaken(body) + amount, old.maximum(), amount >= old.current(), cause);
    return true;
  }

  /** Explicit healing reduces injury; changing block capacity never calls this. */
  public static boolean heal(ServerSubLevel body, double amount) {
    if (!Double.isFinite(amount) || amount <= 0) return false;
    var old = status(body);
    if (old.dead()) return false;
    store(body, Math.max(0, damageTaken(body) - amount), old.maximum(), false, old.causeOfDeath());
    return true;
  }

  private static void store(
      ServerSubLevel body, double injury, double maximum, boolean dead, String cause) {
    maximum = Math.max(0, maximum);
    injury = Math.max(0, injury);
    boolean transaction = SAFE_REMOVALS.get().contains(body);
    double current = Math.clamp(maximum - injury, 0, maximum);
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    var previous = root.getCompound(DATA);
    boolean died = dead || previous.getBoolean("dead") || current == 0 && !transaction;
    var data = new CompoundTag();
    data.putDouble("damage", injury);
    data.putDouble("current", died ? 0 : current);
    data.putDouble("maximum", maximum);
    data.putBoolean("dead", died);
    if (previous.getBoolean("dead")) data.putString("cause", previous.getString("cause"));
    else if (died) data.putString("cause", cause.isEmpty() ? "component_loss" : cause);
    root.put(DATA, data);
    body.setUserDataTag(root);
    if (!died && injury == 0 && maximum > 0 && !transaction)
      org.Azkerash.create_bioships.combat.SubLevelWounds.healEyes(body);
  }
}
