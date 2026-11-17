package org.Azkerash.create_bioships.amoeba.digestion;

import java.util.*;
import net.minecraft.core.*;
import org.Azkerash.create_bioships.*;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;

/** Amoeba-only arrangement decisions; transport owns movement and inventory preservation. */
public final class DigestiveLayoutPlanner {
  public static boolean arrange(LivingCoreBlockEntity core) {
    var host = core.amoebaComponents();
    if (host.body() == null || !host.alive() || ComponentTransport.busy(host)) return false;
    var groups = new ArrayList<>(StomachConnections.groups(core));
    if (groups.size() < 2) return false;
    groups.sort(Comparator.<List<StomachBlockEntity>>comparingInt(List::size).reversed());
    var destination = groups.getFirst();
    if (!destination.getFirst().connected(core)) return false;
    for (var group : groups) {
      if (group == destination || group.size() != 1) continue;
      var organ = group.getFirst();
      if (organ.getPersistentData().getBoolean("BioshipManualPlacement")) continue;
      var candidates =
          new TreeSet<BlockPos>(
              Comparator.comparingDouble((BlockPos p) -> p.distSqr(organ.getBlockPos()))
                  .thenComparingLong(BlockPos::asLong));
      for (var anchor : destination)
        for (var face : Direction.values()) candidates.add(anchor.getBlockPos().relative(face));
      for (var target : candidates) {
        var state = core.getLevel().getBlockState(target);
        if (!state.isAir() && !BioshipTissue.isTissue(state)) continue;
        if (ComponentTransport.begin(host, organ.getBlockPos(), target, false)) return true;
      }
    }
    return false;
  }

  private DigestiveLayoutPlanner() {}
}
