package org.azkerash.createbioships.development.testing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.azkerash.createbioships.development.testing.*;

/** S2C opens the toolbox; C2S requests the existing navigator after server validation. */
@EventBusSubscriber(modid = "create_bioships")
public record DevelopmentToolPayload(boolean navigate) implements CustomPacketPayload {
  public static final Type<DevelopmentToolPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:development_tool"));
  public static final StreamCodec<RegistryFriendlyByteBuf, DevelopmentToolPayload> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeBoolean(p.navigate), b -> new DevelopmentToolPayload(b.readBoolean()));

  public Type<DevelopmentToolPayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) {
    var r = e.registrar("1");
    r.playBidirectional(
        TYPE,
        CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND) {
                    org.azkerash.createbioships.client.development.DevelopmentToolkitScreen.open();
                  } else if (p.navigate
                      && c.player() instanceof ServerPlayer server
                      && DevelopmentToolkitItem.allowed(server)) {
                    DevelopmentNavigatorItem.open(server);
                  }
                }));
  }
}
