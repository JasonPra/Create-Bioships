package org.azkerash.createbioships.mixin;

import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.azkerash.createbioships.development.testing.DevelopmentLoadout;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DevelopmentCreativeSlotMixin {
  @Shadow public ServerPlayer player;

  @Inject(
      method = "handleSetCreativeModeSlot",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void core$keepCreativeNavigator(
      ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
    if (DevelopmentLoadout.active(player)
        && DevelopmentLoadout.reservedMenuSlot(packet.slotNum())) {
      DevelopmentLoadout.ensureNavigator(player);
      DevelopmentLoadout.synchronize(player);
      callback.cancel();
    }
  }

  @Inject(
      method = "handlePlayerAction",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  private void core$keepHandNavigator(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
    if (!DevelopmentLoadout.active(player)) {
      return;
    }
    var action = packet.getAction();
    boolean drop =
        action == ServerboundPlayerActionPacket.Action.DROP_ITEM
            || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
    boolean swap = action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
    if ((drop || swap) && DevelopmentLoadout.managed(player.getMainHandItem())
        || swap && DevelopmentLoadout.protectedItem(player, player.getOffhandItem())) {
      DevelopmentLoadout.synchronize(player);
      callback.cancel();
    }
  }
}
