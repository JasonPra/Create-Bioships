package org.Azkerash.create_bioships.combat;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.Azkerash.create_bioships.Create_bioships;

@EventBusSubscriber(modid = Create_bioships.MODID, bus = EventBusSubscriber.Bus.MOD)
public record WoundPayload(UUID body, BlockPos cell, Direction face, long at, int duration)
    implements CustomPacketPayload {
  public static final Type<WoundPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "wound"));
  public static final StreamCodec<RegistryFriendlyByteBuf, WoundPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeBlockPos(p.cell);
            b.writeEnum(p.face);
            b.writeLong(p.at);
            b.writeVarInt(p.duration);
          },
          b ->
              new WoundPayload(
                  b.readUUID(),
                  b.readBlockPos(),
                  b.readEnum(Direction.class),
                  b.readLong(),
                  b.readVarInt()));

  @Override
  public Type<WoundPayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () ->
                        org.Azkerash.create_bioships.client.combat.SubLevelWoundRenderer.receive(
                            p)));
  }
}
