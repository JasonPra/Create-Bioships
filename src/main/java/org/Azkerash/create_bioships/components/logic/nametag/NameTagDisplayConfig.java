package org.Azkerash.create_bioships.components.logic.nametag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Menu-scoped label offsets in sixteenths of a block; the server validates the open display menu.
 */
@EventBusSubscriber(modid = "create_bioships")
public record NameTagDisplayConfig(int menu, int x, int y, int z) implements CustomPacketPayload {
  public static final Type<NameTagDisplayConfig> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:name_tag_offset"));
  public static final StreamCodec<RegistryFriendlyByteBuf, NameTagDisplayConfig> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeVarInt(p.menu);
            b.writeVarInt(p.x);
            b.writeVarInt(p.y);
            b.writeVarInt(p.z);
          },
          b ->
              new NameTagDisplayConfig(
                  b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));

  public Type<NameTagDisplayConfig> type() {
    return TYPE;
  }

  public static boolean handle(ServerPlayer player, NameTagDisplayConfig p) {
    if (!(player.containerMenu instanceof NameTagDisplayMenu menu)
        || menu.containerId != p.menu
        || menu.getDisplay() == null
        || !menu.stillValid(player)) {
      return false;
    }
    menu.getDisplay().setOffset(p.x, p.y, p.z);
    menu.broadcastChanges();
    return true;
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) {
    e.registrar("1")
        .playToServer(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () -> {
                      if (c.player() instanceof ServerPlayer player) {
                        handle(player, p);
                      }
                    }));
  }
}
