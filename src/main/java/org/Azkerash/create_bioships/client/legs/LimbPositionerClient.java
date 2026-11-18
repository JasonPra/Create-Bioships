package org.Azkerash.create_bioships.client.legs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.compat.letitwalk.*;
import org.Azkerash.create_bioships.components.logic.legs.ModularJointEntity;

@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class LimbPositionerClient {
  @SubscribeEvent
  public static void scroll(InputEvent.MouseScrollingEvent event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.screen != null
        || mc.level == null
        || !LimbPositionerActions.holding(mc.player)
        || event.getScrollDeltaY() == 0) return;
    if (!(mc.hitResult instanceof BlockHitResult hit)
        || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof ModularJointEntity)) return;
    int step = Screen.hasControlDown() ? 1 : 5;
    PacketDistributor.sendToServer(
        new LimbPositionerPayload(
            hit.getBlockPos(), 1, event.getScrollDeltaY() > 0 ? step : -step));
    event.setCanceled(true);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.level == null
        || mc.screen != null
        || !LimbPositionerActions.holding(mc.player)) return;
    if (mc.hitResult instanceof BlockHitResult hit
        && mc.level.getBlockEntity(hit.getBlockPos()) instanceof ModularJointEntity joint)
      mc.player.displayClientMessage(
          Component.translatable("item.create_bioships.limb_positioner.hint", joint.restOffset()),
          true);
  }
}
