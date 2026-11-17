package org.Azkerash.create_bioships;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork;

/** Core adapter for live stomach topology; cached snapshots never own organ inventory or worlds. */
public final class StomachConnections {
  private record Snapshot(
      Map<DigestiveNetwork.Cell, StomachBlockEntity> nodes,
      DigestiveNetwork<StomachBlockEntity> network) {}

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static DigestiveNetwork.Cell cell(BlockPos pos) {
    return new DigestiveNetwork.Cell(pos.getX(), pos.getY(), pos.getZ());
  }

  private static Snapshot snapshot(LivingCoreBlockEntity core) {
    var nodes = new HashMap<DigestiveNetwork.Cell, StomachBlockEntity>();
    if (core.ship() != null)
      for (var stomach : DigestiveSystem.stomachs(core))
        if (BioshipContainerState.entity(core.getLevel(), core.ship(), stomach.getBlockPos())
            == stomach) nodes.put(cell(stomach.getBlockPos()), stomach);
    var reference = CACHE.get(core);
    var existing = reference == null ? null : reference.get();
    if (existing != null && existing.nodes().equals(nodes)) return existing;
    var result = new Snapshot(Map.copyOf(nodes), new DigestiveNetwork<>(nodes));
    CACHE.put(core, new WeakReference<>(result));
    return result;
  }

  public static List<StomachBlockEntity> members(
      LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    var snapshot = snapshot(core);
    var cell = cell(stomach.getBlockPos());
    return snapshot.nodes().get(cell) == stomach ? snapshot.network().members(cell) : List.of();
  }

  public static List<List<StomachBlockEntity>> groups(LivingCoreBlockEntity core) {
    return snapshot(core).network().groups();
  }

  static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    if (core.ship() == null) return false;
    var snapshot = snapshot(core);
    var cell = cell(stomach.getBlockPos());
    return snapshot.nodes().get(cell) == stomach
        && snapshot
            .network()
            .hasConnection(cell, member -> DigestiveSystem.directlyConnected(core, member));
  }

  private StomachConnections() {}
}
