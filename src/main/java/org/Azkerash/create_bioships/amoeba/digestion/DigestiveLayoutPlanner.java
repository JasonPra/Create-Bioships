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
    if (arrangeCube(core)) return true;
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

  /** Fill an existing, validated footprint rather than chasing a moving arrangement target. */
  private static boolean arrangeCube(LivingCoreBlockEntity core) {
    var fixed = new HashSet<BlockPos>();
    for (var cube : StomachMultiblock.formations(core))
      for (var p : cube.members().keySet()) fixed.add(StomachMultiblock.pos(p));
    var organs =
        DigestiveSystem.stomachs(core).stream()
            .filter(s -> !fixed.contains(s.getBlockPos()))
            .toList();
    if (organs.size() < 27) return false;
    var occupied = new HashSet<BlockPos>();
    organs.forEach(s -> occupied.add(s.getBlockPos()));
    var origins =
        new ArrayList<>(
            org.Azkerash.create_bioships.creature.digestion.StomachFormation.origins(
                occupied.stream().map(StomachMultiblock::cell).toList()));
    origins.sort(
        Comparator
            .<org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell>comparingLong(
                origin ->
                    -org.Azkerash.create_bioships
                        .creature
                        .digestion
                        .StomachFormation
                        .cells(origin)
                        .stream()
                        .map(StomachMultiblock::pos)
                        .filter(occupied::contains)
                        .count())
            .thenComparingDouble(
                origin -> StomachMultiblock.pos(origin).distSqr(core.getBlockPos()))
            .thenComparing(Comparator.naturalOrder()));
    for (var origin : origins) {
      var cells =
          org.Azkerash.create_bioships.creature.digestion.StomachFormation.cells(origin).stream()
              .map(StomachMultiblock::pos)
              .toList();
      if (cells.stream()
          .anyMatch(
              p ->
                  fixed.contains(p)
                      || !core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(p))
                      || !occupied.contains(p)
                          && (!DigestiveSystem.permitsPlacement(core, p)
                              || !core.getLevel().getBlockState(p).isAir()
                                  && !BioshipTissue.isTissue(core.getLevel().getBlockState(p)))))
        continue;
      for (var target : cells) {
        if (occupied.contains(target)) continue;
        boolean adjacent = false;
        for (var face : Direction.values())
          if (occupied.contains(target.relative(face))) adjacent = true;
        if (!adjacent) continue;
        for (var organ : organs) {
          if (cells.contains(organ.getBlockPos())
              || organ.getPersistentData().getBoolean("BioshipManualPlacement")) continue;
          if (ComponentTransport.begin(core.amoebaComponents(), organ.getBlockPos(), target, false))
            return true;
        }
      }
    }
    return false;
  }

  private DigestiveLayoutPlanner() {}
}
