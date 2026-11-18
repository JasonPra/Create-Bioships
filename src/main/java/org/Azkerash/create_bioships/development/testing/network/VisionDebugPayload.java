package org.Azkerash.create_bioships.development.testing.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.vision.VisionRaycaster.Ray;

/** World-space samples: endpoints are the actual server collision results, not invented cones. */
public record VisionDebugPayload(UUID body, List<Ray> rays) implements CustomPacketPayload {
  public static final int MAX_RAYS = 416;
  public static final Type<VisionDebugPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:vision_debug"));

  private static void vector(RegistryFriendlyByteBuf b, Vec3 v) {
    b.writeDouble(v.x);
    b.writeDouble(v.y);
    b.writeDouble(v.z);
  }

  private static Vec3 vector(RegistryFriendlyByteBuf b) {
    return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, VisionDebugPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeVarInt(p.rays.size());
            for (var ray : p.rays) {
              vector(b, ray.from());
              vector(b, ray.to());
              b.writeBoolean(ray.blocked());
              b.writeBoolean(ray.unloaded());
            }
          },
          b -> {
            var id = b.readUUID();
            int count = b.readVarInt();
            if (count < 0 || count > MAX_RAYS)
              throw new IllegalArgumentException("Invalid vision sample count");
            var rays = new ArrayList<Ray>();
            for (int i = 0; i < count; i++)
              rays.add(new Ray(vector(b), vector(b), b.readBoolean(), b.readBoolean()));
            return new VisionDebugPayload(id, List.copyOf(rays));
          });

  public Type<VisionDebugPayload> type() {
    return TYPE;
  }
}
