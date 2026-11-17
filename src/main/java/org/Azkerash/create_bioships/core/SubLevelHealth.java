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
    double missing =
        old.contains("maximum")
            ? Math.max(0, old.getDouble("maximum") - old.getDouble("current"))
            : 0;
    store(body, maximum - missing, maximum, old.getBoolean("dead"), old.getString("cause"));
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
        old.current() + delta,
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
    store(body, old.current() - amount, old.maximum(), false, cause);
    return true;
  }

  private static void store(
      ServerSubLevel body, double current, double maximum, boolean dead, String cause) {
    maximum = Math.max(0, maximum);
    current = dead ? 0 : Math.clamp(current, 0, maximum);
    var root = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
    var previous = root.getCompound(DATA);
    var data = new CompoundTag();
    data.putDouble("current", current);
    data.putDouble("maximum", maximum);
    data.putBoolean("dead", dead || current == 0);
    if (dead) data.putString("cause", previous.getString("cause"));
    else if (current == 0) data.putString("cause", cause.isEmpty() ? "component_loss" : cause);
    root.put(DATA, data);
    body.setUserDataTag(root);
  }
}
