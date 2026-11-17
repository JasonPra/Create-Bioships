package org.Azkerash.create_bioships;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell;
import org.Azkerash.create_bioships.creature.digestion.StomachFormation;

/**
 * Live formation adapter. Membership is derived from real organs, never persisted inventory copies.
 */
public final class StomachMultiblock {
  private record Snapshot(
      Map<Cell, StomachBlockEntity> members,
      List<StomachFormation.Cube<StomachBlockEntity>> cubes) {}

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static Cell cell(BlockPos pos) {
    return new Cell(pos.getX(), pos.getY(), pos.getZ());
  }

  public static BlockPos pos(Cell cell) {
    return new BlockPos(cell.x(), cell.y(), cell.z());
  }

  public static List<StomachFormation.Cube<StomachBlockEntity>> formations(
      LivingCoreBlockEntity core) {
    var members = new HashMap<Cell, StomachBlockEntity>();
    for (var group : StomachConnections.groups(core))
      for (var stomach : group) members.put(cell(stomach.getBlockPos()), stomach);
    var ref = CACHE.get(core);
    var previous = ref == null ? null : ref.get();
    if (previous != null && previous.members.equals(members)) return previous.cubes;
    var cubes = StomachFormation.detect(members);
    CACHE.put(core, new WeakReference<>(new Snapshot(Map.copyOf(members), cubes)));
    return cubes;
  }

  public static StomachFormation.Cube<StomachBlockEntity> containing(
      LivingCoreBlockEntity core, BlockPos pos) {
    return formations(core).stream()
        .filter(cube -> cube.contains(cell(pos)))
        .findFirst()
        .orElse(null);
  }

  private StomachMultiblock() {}
}
