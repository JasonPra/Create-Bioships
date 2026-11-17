package org.Azkerash.create_bioships;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork;

/** Core adapter for live stomach topology; cached snapshots never own organ inventory or worlds. */
public final class StomachConnections {
  private record Snapshot(
      UUID body,
      long revision,
      Map<DigestiveNetwork.Cell, StomachBlockEntity> nodes,
      DigestiveNetwork<StomachBlockEntity> network) {}

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static DigestiveNetwork.Cell cell(BlockPos pos) {
    return new DigestiveNetwork.Cell(pos.getX(), pos.getY(), pos.getZ());
  }

  private static Snapshot snapshot(LivingCoreBlockEntity core) {
    UUID body = core.ship() == null ? null : core.ship().getUniqueId();
    long revision = org.Azkerash.create_bioships.integration.sable.BodyRevision.at(core.ship());
    var reference = CACHE.get(core);
    var existing = reference == null ? null : reference.get();
    if (existing != null
        && Objects.equals(body, existing.body())
        && revision == existing.revision()) return existing;
    var nodes = new HashMap<DigestiveNetwork.Cell, StomachBlockEntity>();
    if (core.ship() != null)
      for (var stomach : DigestiveSystem.stomachs(core))
        if (BioshipContainerState.entity(core.getLevel(), core.ship(), stomach.getBlockPos())
            == stomach) nodes.put(cell(stomach.getBlockPos()), stomach);
    var result = new Snapshot(body, revision, Map.copyOf(nodes), new DigestiveNetwork<>(nodes));
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
    return snapshot.nodes().get(cell) == stomach && DigestiveIntakes.connected(core, stomach);
  }

  /**
   * Test the post-departure stomach component, not connectivity that relies on the moving organ.
   */
  static boolean retainsConnection(
      LivingCoreBlockEntity core, StomachBlockEntity member, StomachBlockEntity removed) {
    var nodes = new HashMap<>(snapshot(core).nodes());
    nodes.remove(cell(removed.getBlockPos()));
    var anchors = new HashSet<StomachBlockEntity>();
    DigestiveIntakes.routes(core).routes().forEach(route -> anchors.add(route.stomach()));
    anchors.remove(removed);
    return new DigestiveNetwork<>(nodes)
        .hasConnection(cell(member.getBlockPos()), anchors::contains);
  }

  private StomachConnections() {}
}
