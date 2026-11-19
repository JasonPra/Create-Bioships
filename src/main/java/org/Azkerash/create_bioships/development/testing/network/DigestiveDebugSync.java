package org.Azkerash.create_bioships.development.testing.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.core.SubLevelData;
import org.Azkerash.create_bioships.core.digestion.SubLevelDigestion;

/** Opt-in nearby topology only: no inventories, no per-pipe packets, no background subscribers. */
@EventBusSubscriber(modid = "create_bioships")
public final class DigestiveDebugSync {
  private static final Map<ServerPlayer, Map<UUID, Integer>> VIEWERS = new WeakHashMap<>();

  private DigestiveDebugSync() {}

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToServer(
        DigestiveDebugRequest.TYPE,
        DigestiveDebugRequest.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    if (p.enabled()
                        && org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem
                            .allowed(player)) VIEWERS.put(player, new HashMap<>());
                    else VIEWERS.remove(player);
                  }
                }));
    registrar.playToClient(
        DigestiveDebugPayload.TYPE,
        DigestiveDebugPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () ->
                    org.Azkerash.create_bioships.client.development.IntestineDebugOverlay.receive(
                        p)));
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 != 0 || VIEWERS.isEmpty()) return;
    var iterator = VIEWERS.entrySet().iterator();
    while (iterator.hasNext()) {
      var viewer = iterator.next();
      var player = viewer.getKey();
      if (!event.getServer().getPlayerList().getPlayers().contains(player)
          || !org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem.allowed(
              player)) {
        iterator.remove();
        continue;
      }
      var known = viewer.getValue();
      var present = new HashSet<UUID>();
      var container = SubLevelContainer.getContainer(player.serverLevel());
      if (container == null) continue;
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body
            && !body.isRemoved()
            && SubLevelData.isLiving(body)) {
          var center =
              body.logicalPose()
                  .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
          if (center.distanceToSqr(player.position()) > 128 * 128) continue;
          var id = body.getUniqueId();
          present.add(id);
          int version = SubLevelDigestion.topologyVersion(body);
          if (known.getOrDefault(id, -1) == version) continue;
          var nodes = SubLevelDigestion.debugNodes(body);
          var points =
              nodes.entrySet().stream()
                  .sorted(Map.Entry.comparingByKey())
                  .limit(DigestiveDebugPayload.MAX_NODES)
                  .map(n -> new DigestiveDebugPayload.Node(n.getKey(), n.getValue()))
                  .toList();
          PacketDistributor.sendToPlayer(
              player, new DigestiveDebugPayload(id, points, nodes.size() > points.size()));
          known.put(id, version);
        }
      for (var id : new HashSet<>(known.keySet()))
        if (!present.contains(id)) {
          PacketDistributor.sendToPlayer(player, new DigestiveDebugPayload(id, List.of(), false));
          known.remove(id);
        }
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    VIEWERS.clear();
  }
}
