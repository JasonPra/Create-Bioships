package org.Azkerash.create_bioships.development.testing.network;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.core.locomotion.SubLevelFacing;
import org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem;
import org.Azkerash.create_bioships.equipment.DiagnosticAccess;

/** Per-viewer permission-filtered front markers; nothing is spawned into the shared world. */
@EventBusSubscriber(modid = "create_bioships")
public final class FacingDebugSync {
  private static final Map<ServerPlayer, Set<UUID>> VIEWERS = new WeakHashMap<>();

  private FacingDebugSync() {}

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) {
    var r = e.registrar("1");
    r.playToServer(
        FacingDebugRequest.TYPE,
        FacingDebugRequest.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    if (p.enabled() && DiagnosticAccess.allowed(player))
                      VIEWERS.put(player, new HashSet<>());
                    else VIEWERS.remove(player);
                  }
                }));
    r.playToClient(
        FacingDebugPayload.TYPE,
        FacingDebugPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () ->
                    org.Azkerash.create_bioships.client.development.FacingDebugOverlay.receive(p)));
  }

  public static boolean canSet(ServerSubLevel body, UUID player) {
    return player != null
        && SubLevelCreatureParts.available(body)
        && player.equals(SubLevelPermissions.get(body).owner());
  }

  public static void setFromLook(ServerPlayer player) {
    if (!DevelopmentToolkitItem.allowed(player)) return;
    var eye = player.getEyePosition();
    var hit =
        player
            .serverLevel()
            .clip(
                new ClipContext(
                    eye,
                    eye.add(player.getLookAngle().scale(8)),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player));
    var body =
        hit.getType() == HitResult.Type.BLOCK
            ? CoreAssemblyRules.containing(player.serverLevel(), hit.getBlockPos())
            : null;
    String message;
    if (body == null || !SubLevelData.isLiving(body))
      message = "Look at a living creature's side face.";
    else if (SubLevelCreatureParts.isPart(body) || hit.getDirection().getAxis() == Direction.Axis.Y)
      message = "Choose a side face on the main body.";
    else if (!canSet(body, player.getUUID()))
      message = "Only the creature's owner can change its front.";
    else
      message =
          SubLevelFacing.set(body, hit.getDirection())
              ? "Front set to local " + hit.getDirection().getName() + "."
              : "Cannot change this creature's front.";
    player.displayClientMessage(Component.literal(message), true);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
    if (e.getServer().getTickCount() % 10 != 0 || VIEWERS.isEmpty()) return;
    var it = VIEWERS.entrySet().iterator();
    while (it.hasNext()) {
      var viewer = it.next();
      var player = viewer.getKey();
      if (!e.getServer().getPlayerList().getPlayers().contains(player)
          || !DiagnosticAccess.allowed(player)) {
        it.remove();
        continue;
      }
      var present = new HashSet<UUID>();
      var c = SubLevelContainer.getContainer(player.serverLevel());
      if (c != null)
        for (var entry : c.getAllSubLevels())
          if (entry instanceof ServerSubLevel body
              && !body.isRemoved()
              && SubLevelData.isLiving(body)
              && !SubLevelCreatureParts.isPart(body)
              && DiagnosticAccess.canInspect(body, player)) {
            var box = body.getPlot().getBoundingBox();
            var origin =
                new Vec3(
                    (box.minX() + box.maxX() + 1) / 2.0,
                    box.maxY() + 1.6,
                    (box.minZ() + box.maxZ() + 1) / 2.0);
            if (body.logicalPose().transformPosition(origin).distanceToSqr(player.position())
                    > 96 * 96
                || present.size() >= 16) continue;
            present.add(body.getUniqueId());
            PacketDistributor.sendToPlayer(
                player,
                new FacingDebugPayload(
                    body.getUniqueId(), SubLevelFacing.local(body), origin, true));
          }
      for (var id : viewer.getValue())
        if (!present.contains(id))
          PacketDistributor.sendToPlayer(
              player, new FacingDebugPayload(id, Direction.SOUTH, Vec3.ZERO, false));
      viewer.setValue(present);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
    VIEWERS.clear();
  }
}
