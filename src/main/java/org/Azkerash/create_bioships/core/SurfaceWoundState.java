package org.Azkerash.create_bioships.core;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/**
 * Reusable per-block/per-face wound lifetime data. Visual eye state is forced closed while active.
 */
public final class SurfaceWoundState {
  public record Surface(BlockPos cell, Direction face) {}

  public record Wound(Surface surface, long at, int duration) {
    public boolean active(long now) {
      return now < at + duration;
    }
  }

  private final Map<Surface, Wound> wounds = new LinkedHashMap<>();

  public void hit(BlockPos cell, Direction face, long at, int duration) {
    wounds.values().removeIf(w -> !w.active(at));
    var key = new Surface(cell.immutable(), face);
    wounds.put(key, new Wound(key, at, duration));
    while (wounds.size() > 256) wounds.remove(wounds.keySet().iterator().next());
  }

  public boolean active(BlockPos cell, Direction face, long now) {
    var wound = wounds.get(new Surface(cell, face));
    return wound != null && wound.active(now);
  }

  public List<Wound> active(long now) {
    return wounds.values().stream().filter(w -> w.active(now)).toList();
  }

  public List<Wound> removeIf(java.util.function.Predicate<Surface> predicate) {
    var removed =
        wounds.values().stream().filter(wound -> predicate.test(wound.surface())).toList();
    removed.forEach(wound -> wounds.remove(wound.surface()));
    return removed;
  }

  public ListTag save(long now) {
    var list = new ListTag();
    for (var wound : active(now)) {
      var t = new CompoundTag();
      t.putLong("cell", wound.surface.cell.asLong());
      t.putByte("face", (byte) wound.surface.face.ordinal());
      t.putLong("at", wound.at);
      t.putInt("duration", wound.duration);
      list.add(t);
    }
    return list;
  }

  public static SurfaceWoundState load(ListTag list) {
    var result = new SurfaceWoundState();
    for (var entry : list)
      if (entry instanceof CompoundTag t)
        result.hit(
            BlockPos.of(t.getLong("cell")),
            Direction.from3DDataValue(t.getByte("face")),
            t.getLong("at"),
            t.getInt("duration"));
    return result;
  }
}
