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
    var registrar = event.registrar("2");
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
          if (!org.Azkerash.create_bioships.core.access.SubLevelPermissions.canInspect(
              body, player.getUUID())) continue;
          var center =
              body.logicalPose()
                  .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
          if (center.distanceToSqr(player.position()) > 128 * 128) continue;
          var id = body.getUniqueId();
          present.add(id);
          int version = SubLevelDigestion.topologyVersion(body);
          var nodes = SubLevelDigestion.debugNodes(body);
          var points =
              nodes.entrySet().stream()
                  .sorted(Map.Entry.comparingByKey())
                  .limit(DigestiveDebugPayload.MAX_NODES)
                  .map(n -> inventoryNode(body, n.getKey(), n.getValue()))
                  .toList();
          int signature = 31 * version + points.hashCode();
          if (known.getOrDefault(id, -1) == signature) continue;
          PacketDistributor.sendToPlayer(
              player, new DigestiveDebugPayload(id, points, nodes.size() > points.size()));
          known.put(id, signature);
        }
      for (var id : new HashSet<>(known.keySet()))
        if (!present.contains(id)) {
          PacketDistributor.sendToPlayer(player, new DigestiveDebugPayload(id, List.of(), false));
          known.remove(id);
        }
    }
  }

  public static DigestiveDebugPayload.Node inventoryNode(
      ServerSubLevel body, net.minecraft.core.BlockPos pos, int kind) {
    int slots = 0, used = 0, items = 0;
    var totals = new LinkedHashMap<net.minecraft.resources.ResourceLocation, Integer>();
    var be = body.getLevel().getBlockEntity(pos);
    if (be instanceof com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity sac) {
      var inventory = sac.getInventory();
      slots = inventory.getSlots();
      for (int i = 0; i < slots; i++) {
        var stack = inventory.getStackInSlot(i);
        if (!stack.isEmpty()) {
          used++;
          items += stack.getCount();
          if (kind == 3)
            totals.merge(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getCount(),
                Integer::sum);
        }
      }
    } else if (be instanceof net.minecraft.world.Container inventory) {
      slots = inventory.getContainerSize();
      for (int i = 0; i < slots; i++) {
        var stack = inventory.getItem(i);
        if (!stack.isEmpty()) {
          used++;
          items += stack.getCount();
          if (kind == 3)
            totals.merge(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getCount(),
                Integer::sum);
        }
      }
    }
    return new DigestiveDebugPayload.Node(
        pos,
        kind,
        slots,
        used,
        items,
        totals.entrySet().stream()
            .map(e -> new DigestiveDebugPayload.ItemTotal(e.getKey(), e.getValue()))
            .toList());
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    VIEWERS.clear();
  }
}
