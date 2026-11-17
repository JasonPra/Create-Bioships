package org.Azkerash.create_bioships;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.creature.digestion.DigestiveIntakeRoutes;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell;
import org.Azkerash.create_bioships.registry.Create_bioships;

/** Adapts actual body blocks to reusable intake routing; saved pair assignments are only hints. */
final class DigestiveIntakes {
  private record Snapshot(
      Map<BlockPos, BlockState> states,
      Map<Cell, StomachBlockEntity> stomachs,
      DigestiveIntakeRoutes<StomachBlockEntity> routes) {}

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static Cell cell(BlockPos p) {
    return new Cell(p.getX(), p.getY(), p.getZ());
  }

  static DigestiveIntakeRoutes<StomachBlockEntity> routes(LivingCoreBlockEntity core) {
    var states = new HashMap<BlockPos, BlockState>();
    var stomachs = new HashMap<Cell, StomachBlockEntity>();
    if (core.ship() != null) {
      for (var p : BioshipStructure.cells(core.ship()).keySet()) {
        var state = core.getLevel().getBlockState(p);
        if (IntestineBlock.compatible(state)) states.put(p, state);
      }
      for (var stomach : DigestiveSystem.stomachs(core))
        if (BioshipContainerState.entity(core.getLevel(), core.ship(), stomach.getBlockPos())
            == stomach) stomachs.put(cell(stomach.getBlockPos()), stomach);
    }
    var ref = CACHE.get(core);
    var previous = ref == null ? null : ref.get();
    if (previous != null && previous.states.equals(states) && previous.stomachs.equals(stomachs))
      return previous.routes;
    var mouths = new HashSet<Cell>();
    var tubes = new HashSet<Cell>();
    var links = new HashMap<Cell, Set<Cell>>();
    states.forEach(
        (p, state) -> {
          var here = cell(p);
          if (BioshipIntegration.isMaw(state)) mouths.add(here);
          if (state.is(Create_bioships.INTESTINE.get())) tubes.add(here);
          var neighbors = new HashSet<Cell>();
          for (var direction : net.minecraft.core.Direction.values()) {
            var next = p.relative(direction);
            if (states.containsKey(next) && DigestiveSystem.linked(core, p, next))
              neighbors.add(cell(next));
          }
          links.put(here, neighbors);
        });
    var result = new DigestiveIntakeRoutes<>(stomachs, mouths, tubes, links);
    CACHE.put(
        core, new WeakReference<>(new Snapshot(Map.copyOf(states), Map.copyOf(stomachs), result)));
    return result;
  }

  static StomachBlockEntity forMouth(LivingCoreBlockEntity core, BlockPos mouth) {
    var route = routes(core).route(cell(mouth));
    return route == null ? null : route.stomach();
  }

  private DigestiveIntakes() {}
}
