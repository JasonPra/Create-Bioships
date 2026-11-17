package org.Azkerash.create_bioships.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.Azkerash.create_bioships.Create_bioships;

@EventBusSubscriber(modid = Create_bioships.MODID)
public record ProjectileAttachmentPayload(int entity, ProjectileAttachment.Attachment data)
    implements CustomPacketPayload {
  public static final Type<ProjectileAttachmentPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "embedded_projectile"));

  private static void vector(RegistryFriendlyByteBuf b, Vec3 v) {
    b.writeDouble(v.x);
    b.writeDouble(v.y);
    b.writeDouble(v.z);
  }

  private static Vec3 vector(RegistryFriendlyByteBuf b) {
    return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileAttachmentPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeVarInt(p.entity);
            b.writeUUID(p.data.body());
            b.writeBlockPos(p.data.cell());
            vector(b, p.data.point());
            vector(b, p.data.direction());
            b.writeLong(p.data.expires());
          },
          b ->
              new ProjectileAttachmentPayload(
                  b.readVarInt(),
                  new ProjectileAttachment.Attachment(
                      b.readUUID(), b.readBlockPos(), vector(b), vector(b), b.readLong())));

  @Override
  public Type<ProjectileAttachmentPayload> type() {
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
                    () -> ProjectileAttachment.accept(c.player().level(), p.entity, p.data)));
  }
}
