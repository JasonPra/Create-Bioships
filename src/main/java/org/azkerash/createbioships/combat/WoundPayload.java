package org.azkerash.createbioships.combat;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.azkerash.createbioships.CreateBioships;

@EventBusSubscriber(modid = CreateBioships.MODID)
public record WoundPayload(UUID body, BlockPos cell, Direction face, long at, int duration)
    implements CustomPacketPayload {
  public static final Type<WoundPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath(CreateBioships.MODID, "wound"));
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
                        org.azkerash.createbioships.client.combat.SubLevelWoundRenderer.receive(
                            p)));
  }
}
