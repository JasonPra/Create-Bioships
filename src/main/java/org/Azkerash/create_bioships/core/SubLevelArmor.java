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
    if (cached != null) return cached;
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
    return result;
  }

  private static double spent(ServerSubLevel body) {
    var root = body.getUserDataTag();
    if (root == null) return 0;
    double value = root.getCompound(SubLevelData.ARMOR).getDouble("spent");
    return Double.isFinite(value) ? Math.max(0, value) : 0;
  }

  public static Status status(ServerSubLevel body) {
    double maximum = capacity(body).total;
    return new Status(Math.max(0, maximum - spent(body)), maximum);
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var cached = CAPACITY.get(body);
    if (cached == null) return;
    double previous = cached.cells.getOrDefault(pos.asLong(), 0d);
    double next = ArmorValues.points(state);
    if (previous == next) return;
    if (next == 0) cached.cells.remove(pos.asLong());
    else cached.cells.put(pos.asLong(), next);
    CAPACITY.put(body, new Capacity(cached.cells, Math.max(0, cached.total + next - previous)));
  }

  /** Returns health damage remaining after a hit on an actual armored cell. */
  public static double absorb(
      ServerSubLevel body, BlockPos cell, DamageSource source, double damage) {
    if (!Double.isFinite(damage) || damage <= 0) return 0;
    if (cell == null
        || source.is(DamageTypeTags.BYPASSES_ARMOR)
        || ArmorValues.points(body.getLevel().getBlockState(cell)) == 0
        || SubLevelHealth.status(body).dead()) return damage;
    double absorbed = Math.min(damage, status(body).current);
    if (absorbed > 0) {
      var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
      var tag = root.getCompound(SubLevelData.ARMOR).copy();
      tag.putDouble("spent", spent(body) + absorbed);
      root.put(SubLevelData.ARMOR, tag);
      body.setUserDataTag(root);
    }
    return damage - absorbed;
  }
}
