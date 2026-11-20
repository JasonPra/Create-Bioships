package org.azkerash.createbioships.components.logic.diagnostics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;

@EventBusSubscriber(modid = "create_bioships")
public final class DebugPanelControl {
  private static final int REFRESH_TICKS = 20;
  private static final double REACH_BLOCKS = 8;
  private static final Map<ServerPlayer, Long> NEXT_REFRESH = new WeakHashMap<>();

  private DebugPanelControl() {}

  public static boolean usable(ServerPlayer player, ServerSubLevel physical, BlockPos panel) {
    return !physical.isRemoved()
        && player.level() == physical.getLevel()
        && physical.getLevel().hasChunkAt(panel)
        && CoreAssemblyRules.containing(physical.getLevel(), panel) == physical
        && physical.getLevel().getBlockState(panel).is(ComponentRegistry.DEBUG_PANEL.get())
        && SubLevelPermissions.canInspect(physical, player.getUUID())
        && player
                .getEyePosition()
                .distanceToSqr(physical.logicalPose().transformPosition(Vec3.atCenterOf(panel)))
            <= REACH_BLOCKS * REACH_BLOCKS;
  }

  public static void open(ServerPlayer player, ServerSubLevel physical, BlockPos panel) {
    if (!usable(player, physical, panel)) {
      player.displayClientMessage(
          net.minecraft.network.chat.Component.literal(
              "Diagnostics require permission and a nearby connected panel."),
          true);
      return;
    }
    send(player, physical, panel, true);
  }

  private static void send(
      ServerPlayer player, ServerSubLevel physical, BlockPos panel, boolean opening) {
    long now = physical.getLevel().getGameTime();
    if (now < NEXT_REFRESH.getOrDefault(player, Long.MIN_VALUE)) {
      return;
    }
    NEXT_REFRESH.put(player, now + REFRESH_TICKS);
    PacketDistributor.sendToPlayer(
        player,
        new DebugPanelPayload(
            SubLevelCreatureParts.root(physical).getUniqueId(),
            panel,
            now,
            opening,
            CreatureDiagnostics.inspect(SubLevelCreatureParts.root(physical))));
  }

  public static boolean refresh(ServerPlayer player, DebugPanelPayload.Request request) {
    var level = player.serverLevel();
    if (!level.hasChunkAt(request.panel())) {
      return false;
    }
    var physical = CoreAssemblyRules.containing(level, request.panel());
    if (physical == null
        || !SubLevelCreatureParts.root(physical).getUniqueId().equals(request.grid())
        || !usable(player, physical, request.panel())) {
      return false;
    }
    send(player, physical, request.panel(), false);
    return true;
  }

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToServer(
        DebugPanelPayload.Request.TYPE,
        DebugPanelPayload.Request.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) {
                    refresh(player, p);
                  }
                }));
    registrar.playToClient(
        DebugPanelPayload.TYPE,
        DebugPanelPayload.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> org.azkerash.createbioships.client.components.DebugPanelScreen.receive(p)));
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    NEXT_REFRESH.clear();
  }
}
