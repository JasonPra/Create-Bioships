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
      UUID body,
      long revision,
      DigestiveIntakeRoutes<StomachBlockEntity> routes,
      Map<StomachBlockEntity, List<net.minecraft.world.level.block.entity.BlockEntity>> stores,
      Set<StomachBlockEntity> functioning) {}

  private static final Map<LivingCoreBlockEntity, WeakReference<Snapshot>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static Cell cell(BlockPos p) {
    return new Cell(p.getX(), p.getY(), p.getZ());
  }

  static DigestiveIntakeRoutes<StomachBlockEntity> routes(LivingCoreBlockEntity core) {
    return snapshot(core).routes();
  }

  static List<net.minecraft.world.level.block.entity.BlockEntity> connectedStores(
      LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    return snapshot(core).stores().getOrDefault(stomach, List.of());
  }

  static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    return snapshot(core).functioning().contains(stomach);
  }

  private static Snapshot snapshot(LivingCoreBlockEntity core) {
    UUID body = core.ship() == null ? null : core.ship().getUniqueId();
    long revision = org.Azkerash.create_bioships.integration.sable.BodyRevision.at(core.ship());
    var ref = CACHE.get(core);
    var previous = ref == null ? null : ref.get();
    if (previous != null
        && Objects.equals(body, previous.body())
        && revision == previous.revision()) return previous;
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
    var stores = new HashMap<Cell, net.minecraft.world.level.block.entity.BlockEntity>();
    if (core.ship() != null)
      for (var p : states.keySet()) {
        var entity = BioshipContainerState.entity(core.getLevel(), core.ship(), p);
        if (entity != null
            && org.Azkerash.create_bioships.integration.biomancy.NativeStorage.isStorage(entity))
          stores.put(cell(p), entity);
      }
    var storageRoutes = new DigestiveIntakeRoutes<>(stomachs, stores.keySet(), tubes, links);
    var byStomach =
        new HashMap<StomachBlockEntity, List<net.minecraft.world.level.block.entity.BlockEntity>>();
    for (var route : storageRoutes.routes())
      byStomach
          .computeIfAbsent(route.stomach(), ignored -> new ArrayList<>())
          .add(stores.get(route.mouth()));
    var groups = StomachConnections.groups(core);
    var sharedStores =
        new HashMap<StomachBlockEntity, List<net.minecraft.world.level.block.entity.BlockEntity>>();
    var anchors = new HashSet<StomachBlockEntity>();
    result.routes().forEach(route -> anchors.add(route.stomach()));
    var functioning = new HashSet<StomachBlockEntity>();
    for (var group : groups) {
      var union =
          new TreeMap<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>(
              Comparator.comparingLong(BlockPos::asLong));
      for (var organ : group)
        for (var store : byStomach.getOrDefault(organ, List.of()))
          union.put(store.getBlockPos(), store);
      var list = List.copyOf(union.values());
      for (var organ : group) sharedStores.put(organ, list);
      if (group.stream().anyMatch(anchors::contains)) functioning.addAll(group);
    }
    var snapshot =
        new Snapshot(body, revision, result, Map.copyOf(sharedStores), Set.copyOf(functioning));
    CACHE.put(core, new WeakReference<>(snapshot));
    return snapshot;
  }

  static StomachBlockEntity forMouth(LivingCoreBlockEntity core, BlockPos mouth) {
    return routes(core).routes(cell(mouth)).stream()
        .map(DigestiveIntakeRoutes.Route::stomach)
        .min(Comparator.comparingInt(StomachBlockEntity::queuedFood))
        .orElse(null);
  }

  private DigestiveIntakes() {}
}
