package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;

/** The sublevel owns persistent health and death; blocks only contribute capacity. */
public final class SubLevelHealth {
  public static final String DATA = SubLevelData.HEALTH;
  private static final Map<ServerSubLevel, Map<Long, Double>> CELLS = new WeakHashMap<>();

  private static final ThreadLocal<Set<ServerSubLevel>> SAFE_REMOVALS =
      ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

  private SubLevelHealth() {}

  public static boolean initialized(ServerSubLevel body) {
    return CELLS.containsKey(body);
  }

  public static java.util.List<BlockPos> blocks(ServerSubLevel body) {
    initialize(body);
    return CELLS.get(body).keySet().stream().map(BlockPos::of).toList();
  }

  public static SubLevelData.Health status(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) {
      return SubLevelData.health(body);
    }
    initialize(body);
    return SubLevelData.health(body);
  }

  private static void initialize(ServerSubLevel body) {
    if (CELLS.containsKey(body)) {
      return;
    }
    var cells = new HashMap<Long, Double>();
    double maximum = 0;
    for (var holder : body.getPlot().getLoadedChunks()) {
      var chunk = holder.getChunk();
      var sections = chunk.getSections();
      for (int i = 0; i < sections.length; i++) {
        var section = sections[i];
        if (section.hasOnlyAir()) {
          continue;
        }
        int floor = chunk.getSectionYFromSectionIndex(i) << 4;
        for (int x = 0; x < 16; x++) {
          for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
              double hp = BlockHealthValues.health(section.getBlockState(x, y, z));
              if (hp == 0) {
                continue;
              }
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
      }
    }
    CELLS.put(body, cells);
    SubLevelHunger.initializeCapacity(body, cells.keySet());
    SubLevelOxygen.initialize(body, cells.keySet());
    if (SubLevelCreatureParts.isPart(body)) {
      SubLevelCreatureParts.healthContribution(body, maximum);
      return;
    }
    maximum += SubLevelCreatureParts.extraHealth(body);
    var old = SubLevelData.health(body);
    store(body, old.damage(), maximum, old.dead(), old.causeOfDeath());
  }

  private static double damageTaken(ServerSubLevel body) {
    return SubLevelData.health(body).damage();
  }

  /** Create owns pickup/drops; suppress only damage from the resulting capacity change. */
  public static <T> T withoutRemovalDamage(
      ServerSubLevel body, java.util.function.Supplier<T> removal) {
    body = SubLevelCreatureParts.root(body);
    var before = status(body);
    double beforeDamage = damageTaken(body);
    var active = SAFE_REMOVALS.get();
    boolean added = active.add(body);
    try {
      return removal.get();
    } finally {
      var after = status(body);
      if (added) {
        active.remove(body);
      }
      if (active.isEmpty()) {
        SAFE_REMOVALS.remove();
      }
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
    if ((!CoreAssemblyRules.isCreature(body) && !SubLevelData.isLiving(body))
        || !CELLS.containsKey(body)) {
      return;
    }
    SubLevelHunger.blockChanged(body, pos, state);
    SubLevelOxygen.changed(body, pos, state);
    var cells = CELLS.get(body);
    double previous = cells.getOrDefault(pos.asLong(), 0d), next = BlockHealthValues.health(state);
    if (previous == next) {
      return;
    }
    if (next == 0) {
      cells.remove(pos.asLong());
    } else {
      cells.put(pos.asLong(), next);
    }
    if (SubLevelCreatureParts.isPart(body)) {
      SubLevelCreatureParts.healthContribution(
          body, cells.values().stream().mapToDouble(Double::doubleValue).sum());
      return;
    }
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
    if (!SubLevelCreatureParts.available(body)) {
      return false;
    }
    body = SubLevelCreatureParts.root(body);
    if (!Double.isFinite(amount) || amount <= 0) {
      return false;
    }
    var old = status(body);
    if (old.dead()) {
      return false;
    }
    store(body, damageTaken(body) + amount, old.maximum(), amount >= old.current(), cause);
    return true;
  }

  /** Explicit healing reduces injury; changing block capacity never calls this. */
  public static boolean heal(ServerSubLevel body, double amount) {
    if (!SubLevelCreatureParts.available(body)) {
      return false;
    }
    body = SubLevelCreatureParts.root(body);
    if (!Double.isFinite(amount) || amount <= 0) {
      return false;
    }
    var old = status(body);
    if (old.dead() || SubLevelData.oxygen(body).value() <= 0) {
      return false;
    }
    store(body, Math.max(0, damageTaken(body) - amount), old.maximum(), false, old.causeOfDeath());
    return true;
  }

  public static double localMaximum(ServerSubLevel body) {
    initialize(body);
    return CELLS.get(body).values().stream().mapToDouble(Double::doubleValue).sum();
  }

  public static void refreshCapacity(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    initialize(body);
    var old = SubLevelData.health(body);
    store(
        body,
        old.damage(),
        localMaximum(body) + SubLevelCreatureParts.extraHealth(body),
        old.dead(),
        "component_loss");
  }

  private static void store(
      ServerSubLevel body, double injury, double maximum, boolean dead, String cause) {
    maximum = Math.max(0, maximum);
    injury = Math.max(0, injury);
    boolean transaction = SAFE_REMOVALS.get().contains(body);
    double current = Math.clamp(maximum - injury, 0, maximum);
    var previous = SubLevelData.health(body);
    boolean died = dead || previous.dead() || current == 0 && !transaction;
    String deathCause =
        previous.dead()
            ? previous.causeOfDeath()
            : died ? cause.isEmpty() ? "component_loss" : cause : "";
    SubLevelData.health(
        body, new SubLevelData.Health(died ? 0 : current, maximum, injury, died, deathCause));
    if (died && !previous.dead()) {
      SubLevelCreatureParts.died(body);
    }
    if (!died && injury == 0 && maximum > 0 && !transaction) {
      org.azkerash.createbioships.combat.SubLevelWounds.healEyes(body);
    }
  }
}
