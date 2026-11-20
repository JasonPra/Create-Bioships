package org.Azkerash.create_bioships.development.testing.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.core.pathfinding.*;

/** Opt-in nearby debug snapshots only. Path following runs without a debug subscriber. */
@EventBusSubscriber(modid = "create_bioships")
public final class PathDebugSync {
  private static final Map<ServerPlayer, Set<UUID>> VIEWERS = new WeakHashMap<>();

  private PathDebugSync() {}

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    var r = event.registrar("1");
    r.playToServer(
        PathDebugRequest.TYPE,
        PathDebugRequest.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    if (p.enabled()
                        && org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem
                            .allowed(player)) {
                      VIEWERS.put(player, new HashSet<>());
                    } else {
                      VIEWERS.remove(player);
                    }
                  }
                }));
    r.playToClient(
        PathDebugPayload.TYPE,
        PathDebugPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> org.Azkerash.create_bioships.client.development.PathDebugOverlay.receive(p)));
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 10 != 0) {
      return;
    }
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
      if (container != null) {
        for (var entry : container.getAllSubLevels()) {
          if (entry instanceof ServerSubLevel body
              && !body.isRemoved()
              && !org.Azkerash.create_bioships.core.SubLevelCreatureParts.isPart(body)
              && SubLevelPaths.get(body) != null) {
            if (!org.Azkerash.create_bioships.core.access.SubLevelPermissions.canInspect(
                body, player.getUUID())) {
              continue;
            }
            var center =
                body.logicalPose()
                    .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
            if (center.distanceToSqr(player.position()) > 96 * 96 || present.size() >= 16) {
              continue;
            }
            var route = SubLevelPaths.get(body);
            if (route == null) {
              continue;
            }
            present.add(body.getUniqueId());
            PacketDistributor.sendToPlayer(
                player,
                new PathDebugPayload(
                    body.getUniqueId(),
                    route.mode(),
                    SubLevelPathfinder.position(body),
                    route.points(),
                    route.next()));
          }
        }
      }
      for (var id : viewer.getValue()) {
        if (!present.contains(id)) {
          PacketDistributor.sendToPlayer(
              player,
              new PathDebugPayload(id, SubLevelPathfinder.Mode.GROUND, Vec3.ZERO, List.of(), 0));
        }
      }
      viewer.setValue(present);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    VIEWERS.clear();
  }
}
