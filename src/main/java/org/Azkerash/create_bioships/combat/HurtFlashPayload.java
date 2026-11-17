package org.Azkerash.create_bioships.combat;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.Azkerash.create_bioships.Create_bioships;

/** Server notification contains identity only; the client owns flash duration and rendering. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public record HurtFlashPayload(UUID body) implements CustomPacketPayload {
  public static final Type<HurtFlashPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "hurt_flash"));
  public static final StreamCodec<RegistryFriendlyByteBuf, HurtFlashPayload> CODEC =
      StreamCodec.of(
          (buf, p) -> buf.writeUUID(p.body()), buf -> new HurtFlashPayload(buf.readUUID()));

  @Override
  public Type<HurtFlashPayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            TYPE,
            CODEC,
            (payload, context) ->
                context.enqueueWork(
                    () ->
                        org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.flash(
                            payload.body())));
  }
}
