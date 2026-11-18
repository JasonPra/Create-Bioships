package org.Azkerash.create_bioships.compat.biomancy.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.core.SubLevelData;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;

/** One changed permission snapshot per nearby grid, never a packet or ticker per membrane. */
@EventBusSubscriber(modid = "create_bioships")
public final class MembraneAccessSync {
  private record Viewer(
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
      Map<UUID, MembraneAccessPayload> known) {}

  private static final Map<ServerPlayer, Viewer> VIEWERS = new WeakHashMap<>();

  private MembraneAccessSync() {}

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            MembraneAccessPayload.TYPE,
            MembraneAccessPayload.CODEC,
            (p, c) ->
                c.enqueueWork(
                    () ->
                        org.Azkerash.create_bioships.client.components.ClientMembraneAccess.receive(
                            p)));
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 5 != 0) return;
    VIEWERS.keySet().retainAll(event.getServer().getPlayerList().getPlayers());
    for (var player : event.getServer().getPlayerList().getPlayers()) {
      var old = VIEWERS.get(player);
      var known =
          old == null || !old.dimension.equals(player.level().dimension())
              ? new HashMap<UUID, MembraneAccessPayload>()
              : old.known;
      if (old != null && known != old.known)
        for (var id : old.known.keySet())
          PacketDistributor.sendToPlayer(player, new MembraneAccessPayload(id, 0, List.of()));
      var present = new HashSet<UUID>();
      var container = SubLevelContainer.getContainer(player.serverLevel());
      if (container != null)
        for (var entry : container.getAllSubLevels())
          if (entry instanceof ServerSubLevel body
              && !body.isRemoved()
              && SubLevelData.isLiving(body)) {
            // Nearest point of the grid's world bounds also covers very large structures.
            var bounds = body.boundingBox().toMojang();
            var p = player.position();
            var near =
                new Vec3(
                    Math.clamp(p.x, bounds.minX, bounds.maxX),
                    Math.clamp(p.y, bounds.minY, bounds.maxY),
                    Math.clamp(p.z, bounds.minZ, bounds.maxZ));
            if (near.distanceToSqr(p) > 128 * 128) continue;
            var access = SubLevelPermissions.get(body);
            var ids = new ArrayList<UUID>();
            if (access.owner() != null) ids.add(access.owner());
            for (var allowed : access.allowed()) ids.add(allowed.id());
            var snapshot = new MembraneAccessPayload(body.getUniqueId(), 2, List.copyOf(ids));
            present.add(body.getUniqueId());
            if (!snapshot.equals(known.put(body.getUniqueId(), snapshot)))
              PacketDistributor.sendToPlayer(player, snapshot);
          }
      for (var id : new HashSet<>(known.keySet()))
        if (!present.contains(id)) {
          PacketDistributor.sendToPlayer(player, new MembraneAccessPayload(id, 0, List.of()));
          known.remove(id);
        }
      VIEWERS.put(player, new Viewer(player.level().dimension(), known));
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    VIEWERS.clear();
  }
}
