package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.logic.armor.ArmorValues;

/** One saved spent-armor ledger per grid; topology changes never repair it. */
public final class SubLevelArmor {
  private record Capacity(Map<Long, Double> cells, double total) {}

  public record Status(double current, double maximum) {}

  private static final Map<ServerSubLevel, Capacity> CAPACITY = new WeakHashMap<>();

  private SubLevelArmor() {}

  private static Capacity capacity(ServerSubLevel body) {
    var cached = CAPACITY.get(body);
    if (cached != null) {
      return cached;
    }
    var cells = new HashMap<Long, Double>();
    double total = 0;
    for (var pos : SubLevelHealth.blocks(body)) {
      double points = ArmorValues.points(body.getLevel().getBlockState(pos));
      if (points > 0) {
        cells.put(pos.asLong(), points);
        total += points;
      }
    }
    var result = new Capacity(cells, total);
    CAPACITY.put(body, result);
    if (SubLevelCreatureParts.isPart(body)) {
      SubLevelCreatureParts.armorContribution(body, total);
    }
    return result;
  }

  private static double spent(ServerSubLevel body) {
    var root = body.getUserDataTag();
    if (root == null) {
      return 0;
    }
    double value = root.getCompound(SubLevelData.ARMOR).getDouble("spent");
    return Double.isFinite(value) ? Math.max(0, value) : 0;
  }

  public static double localMaximum(ServerSubLevel body) {
    return capacity(body).total;
  }

  public static Status status(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) {
      return new Status(0, 0);
    }
    body = SubLevelCreatureParts.root(body);
    double maximum = capacity(body).total + SubLevelCreatureParts.extraArmor(body);
    return new Status(Math.max(0, maximum - spent(body)), maximum);
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var cached = CAPACITY.get(body);
    if (cached == null) {
      return;
    }
    double previous = cached.cells.getOrDefault(pos.asLong(), 0d);
    double next = ArmorValues.points(state);
    if (previous == next) {
      return;
    }
    if (next == 0) {
      cached.cells.remove(pos.asLong());
    } else {
      cached.cells.put(pos.asLong(), next);
    }
    double total = Math.max(0, cached.total + next - previous);
    CAPACITY.put(body, new Capacity(cached.cells, total));
    if (SubLevelCreatureParts.isPart(body)) {
      SubLevelCreatureParts.armorContribution(body, total);
    }
  }

  /**
   * Repairs the saved wear ledger only after health is fully restored and life support permits it.
   */
  public static boolean repair(ServerSubLevel body, double amount) {
    if (!SubLevelCreatureParts.available(body) || !Double.isFinite(amount) || amount <= 0) {
      return false;
    }
    body = SubLevelCreatureParts.root(body);
    var health = SubLevelHealth.status(body);
    if (health.dead()
        || health.current() < health.maximum()
        || !SubLevelHunger.canRegenerate(body)
        || status(body).maximum() <= 0
        || spent(body) <= 0) {
      return false;
    }
    var root = body.getUserDataTag().copy();
    var armor = root.getCompound(SubLevelData.ARMOR).copy();
    armor.putDouble("spent", Math.max(0, spent(body) - amount));
    root.put(SubLevelData.ARMOR, armor);
    body.setUserDataTag(root);
    return true;
  }

  /** Returns health damage remaining after a hit on an actual armored cell. */
  public static double absorb(
      ServerSubLevel body, BlockPos cell, DamageSource source, double damage) {
    return absorb(body, cell, null, source, damage);
  }

  public static double absorb(
      ServerSubLevel body,
      BlockPos cell,
      net.minecraft.core.Direction face,
      DamageSource source,
      double damage) {
    if (!Double.isFinite(damage) || damage <= 0) {
      return 0;
    }
    if (!SubLevelCreatureParts.available(body)) {
      return damage;
    }
    var compact =
        body.getUserDataTag() == null
            ? new CompoundTag()
            : body.getUserDataTag()
                .getCompound(
                    org.Azkerash.create_bioships.compat.letitwalk.CompactLimbController.PART);
    if (!compact.isEmpty() && SubLevelCreatureParts.isPart(body)) {
      cell = BlockPos.of(compact.getLong("Socket"));
      body = SubLevelCreatureParts.root(body);
    }
    if (cell == null
        || source.is(DamageTypeTags.BYPASSES_ARMOR)
        || !ArmorValues.protects(body.getLevel().getBlockState(cell), face)
        || SubLevelHealth.status(body).dead()) {
      return damage;
    }
    if (SubLevelCreatureParts.isPart(body)) {
      capacity(body);
    }
    double absorbed = Math.min(damage, status(body).current);
    if (absorbed > 0) {
      body = SubLevelCreatureParts.root(body);
      var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
      var tag = root.getCompound(SubLevelData.ARMOR).copy();
      tag.putDouble("spent", spent(body) + absorbed);
      root.put(SubLevelData.ARMOR, tag);
      body.setUserDataTag(root);
    }
    return damage - absorbed;
  }
}
