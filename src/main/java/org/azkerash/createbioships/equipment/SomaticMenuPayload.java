package org.azkerash.createbioships.equipment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_bioships")
public record SomaticMenuPayload() implements CustomPacketPayload {
  public static final Type<SomaticMenuPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:somatic_menu"));
  public static final StreamCodec<RegistryFriendlyByteBuf, SomaticMenuPayload> CODEC =
      StreamCodec.unit(new SomaticMenuPayload());

  public Type<SomaticMenuPayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) {
    e.registrar("1")
        .playToClient(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () ->
                        org.azkerash.createbioships.client.equipment.SomaticResonatorScreen
                            .open()));
  }
}
