package org.Azkerash.create_bioships.development.testing.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.core.vision.SubLevelVision;

/** Opt-in nearby debug snapshots only. Vision itself runs without a debug subscriber. */
@EventBusSubscriber(modid = "create_bioships")
public final class VisionDebugSync {
  private static final Map<ServerPlayer, Set<UUID>> VIEWERS = new WeakHashMap<>();

  private VisionDebugSync() {}

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    var r = event.registrar("1");
    r.playToServer(
        VisionDebugRequest.TYPE,
        VisionDebugRequest.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    if (p.enabled()
                        && org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem
                            .allowed(player)) VIEWERS.put(player, new HashSet<>());
                    else VIEWERS.remove(player);
                  }
                }));
    r.playToClient(
        VisionDebugPayload.TYPE,
        VisionDebugPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () ->
                    org.Azkerash.create_bioships.client.development.VisionDebugOverlay.receive(p)));
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 10 != 0) return;
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
      var present = new HashSet<UUID>();
      var container = SubLevelContainer.getContainer(player.serverLevel());
      if (container != null)
        for (var entry : container.getAllSubLevels())
          if (entry instanceof ServerSubLevel body && SubLevelVision.active(body)) {
            var center =
                body.logicalPose()
                    .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
            if (center.distanceToSqr(player.position()) > 96 * 96 || present.size() >= 16) continue;
            var rays =
                SubLevelVision.sights(body).stream()
                    .flatMap(s -> s.rays().stream())
                    .limit(VisionDebugPayload.MAX_RAYS)
                    .toList();
            if (rays.isEmpty()) continue;
            present.add(body.getUniqueId());
            PacketDistributor.sendToPlayer(
                player, new VisionDebugPayload(body.getUniqueId(), rays));
          }
      for (var id : viewer.getValue())
        if (!present.contains(id))
          PacketDistributor.sendToPlayer(player, new VisionDebugPayload(id, List.of()));
      viewer.setValue(present);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    VIEWERS.clear();
  }
}
