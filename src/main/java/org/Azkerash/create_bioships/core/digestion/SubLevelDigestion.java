package org.Azkerash.create_bioships.core.digestion;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;
import org.Azkerash.create_bioships.components.logic.stomach.*;
import org.Azkerash.create_bioships.core.*;

/**
 * One cached topology and simulation dispatcher per living grid. No pipe or organ block tickers.
 */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class SubLevelDigestion {
  public static final int TRANSFER_INTERVAL = 8, MAX_TRANSFERS = 64;
  private static int nextVersion;
  private static final Map<ServerSubLevel, Network> NETWORKS = new IdentityHashMap<>();

  public record Group(List<BlockPos> mouths, List<BlockPos> stomachs, List<BlockPos> sacs) {}

  private static final class Network {
    final ServerSubLevel body;
    final Map<BlockPos, Integer> nodes = new HashMap<>();
    List<Group> groups = List.of();
    boolean dirty = true;
    int cursor, version = ++nextVersion;

    Network(ServerSubLevel body) {
      this.body = body;
      for (var pos : SubLevelHealth.blocks(body)) {
        int role = role(body.getLevel().getBlockState(pos));
        if (role != 0) nodes.put(pos.immutable(), role);
      }
    }

    void rebuild() {
      if (!dirty) return;
      var unseen = new HashSet<>(nodes.keySet());
      var result = new ArrayList<Group>();
      while (!unseen.isEmpty()) {
        var queue = new ArrayDeque<BlockPos>();
        var mouths = new ArrayList<BlockPos>();
        var stomachs = new ArrayList<BlockPos>();
        var sacs = new ArrayList<BlockPos>();
        var first = unseen.iterator().next();
        unseen.remove(first);
        queue.add(first);
        while (!queue.isEmpty()) {
          var pos = queue.removeFirst();
          int role = nodes.get(pos);
          if (role == 2) mouths.add(pos);
          if (role == 3) stomachs.add(pos);
          if (role == 4) sacs.add(pos);
          for (var direction : Direction.values()) {
            var next = pos.relative(direction);
            if (connected(role, nodes.getOrDefault(next, 0), direction) && unseen.remove(next))
              queue.add(next);
          }
        }
        mouths.sort(BlockPos::compareTo);
        stomachs.sort(BlockPos::compareTo);
        sacs.sort(
            Comparator.comparingInt((BlockPos p) -> p.getY())
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        result.add(new Group(List.copyOf(mouths), List.copyOf(stomachs), List.copyOf(sacs)));
      }
      for (var entry : nodes.entrySet())
        if (entry.getValue() == 1) {
          var state = body.getLevel().getBlockState(entry.getKey());
          var next = state;
          for (var face : Direction.values())
            next =
                next.setValue(
                    org.Azkerash.create_bioships.components.logic.intestine.IntestineBlock
                        .CONNECTIONS.get(face),
                    org.Azkerash.create_bioships.components.logic.intestine.IntestineBlock
                        .compatible(
                            body.getLevel().getBlockState(entry.getKey().relative(face)), face));
          if (!next.equals(state)) body.getLevel().setBlock(entry.getKey(), next, 2);
        }
      groups = List.copyOf(result);
      dirty = false;
    }
  }

  private SubLevelDigestion() {}

  /** Storage sacs expose top/bottom ports only. The debug renderer uses the same rule. */
  public static boolean connected(int from, int to, Direction direction) {
    return from != 0
        && to != 0
        && ((from != 4 && to != 4) || direction.getAxis() == Direction.Axis.Y);
  }

  private static int role(BlockState state) {
    if (state.getBlock() instanceof com.github.elenterius.biomancy.block.storagesac.StorageSacBlock)
      return 4;
    return state.is(ComponentRegistry.INTESTINE.get())
        ? 1
        : state.is(ComponentRegistry.MOUTH.get())
            ? 2
            : state.is(ComponentRegistry.STOMACH.get()) ? 3 : 0;
  }

  public static void changed(ServerSubLevel body, BlockPos pos, BlockState state) {
    var network = NETWORKS.get(body);
    if (network == null) return;
    int role = role(state), old = network.nodes.getOrDefault(pos, 0);
    if (role == old) return;
    if (role == 0) network.nodes.remove(pos);
    else network.nodes.put(pos.immutable(), role);
    network.dirty = true;
    network.version = ++nextVersion;
  }

  public static List<Group> groups(ServerSubLevel body) {
    var network = NETWORKS.computeIfAbsent(body, Network::new);
    network.rebuild();
    return network.groups;
  }

  public static int topologyVersion(ServerSubLevel body) {
    groups(body);
    return NETWORKS.get(body).version;
  }

  public static Map<BlockPos, Integer> debugNodes(ServerSubLevel body) {
    groups(body);
    return Map.copyOf(NETWORKS.get(body).nodes);
  }

  public static int transfer(MouthBlockEntity mouth) {
    if (!(mouth.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return 0;
    var body = CoreAssemblyRules.containing(level, mouth.getBlockPos());
    if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead())
      return 0;
    var network = NETWORKS.get(body);
    for (var group : groups(body))
      if (group.mouths.contains(mouth.getBlockPos()))
        return StomachStorageRouting.intake(
            mouth, group.stomachs, storageWindow(group.sacs, network == null ? 0 : network.cursor));
    return 0;
  }

  private static List<BlockPos> storageWindow(List<BlockPos> sacs, int cursor) {
    if (sacs.size() <= 8) return sacs;
    var result = new ArrayList<BlockPos>();
    for (int i = 0; i < 8; i++) result.add(sacs.get(Math.floorMod(cursor + i, sacs.size())));
    return result;
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    var active = Collections.newSetFromMap(new IdentityHashMap<ServerSubLevel, Boolean>());
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body
            && SubLevelData.isLiving(body)
            && !body.isRemoved()) {
          active.add(body);
          var groups = groups(body);
          if (SubLevelHealth.status(body).dead()) continue;
          for (var group : groups)
            for (var pos : group.stomachs)
              if (level.getBlockEntity(pos) instanceof StomachBlockEntity stomach)
                StomachDigestion.advance(stomach);
          if (level.getGameTime() % TRANSFER_INTERVAL == 0) {
            var mouths = groups.stream().flatMap(g -> g.mouths.stream()).toList();
            var network = NETWORKS.get(body);
            int count = Math.min(MAX_TRANSFERS, mouths.size());
            for (int i = 0; i < count; i++) {
              var pos = mouths.get(Math.floorMod(network.cursor++, mouths.size()));
              if (level.getBlockEntity(pos) instanceof MouthBlockEntity mouth) transfer(mouth);
            }
            int remaining = MAX_TRANSFERS;
            for (var group : groups)
              if (remaining > 0)
                remaining -=
                    StomachStorageRouting.compactStacks(
                        level, group.sacs, network.cursor++, remaining);
            for (var group : groups)
              for (var pos : group.stomachs)
                if (remaining > 0
                    && level.getBlockEntity(pos) instanceof StomachBlockEntity stomach) {
                  remaining -=
                      Math.max(
                          1,
                          StomachStorageRouting.service(
                              stomach, storageWindow(group.sacs, network.cursor++), remaining));
                }
          }
        }
    }
    NETWORKS.keySet().retainAll(active);
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    NETWORKS.clear();
  }
}
