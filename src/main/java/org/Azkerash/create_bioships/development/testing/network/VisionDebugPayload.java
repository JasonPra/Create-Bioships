package org.Azkerash.create_bioships.development.testing.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.vision.VisionRaycaster.Ray;

/** World-space samples: endpoints are the actual server collision results, not invented cones. */
public record VisionDebugPayload(UUID body, List<Ray> rays, List<Vec3> targets)
    implements CustomPacketPayload {
  public VisionDebugPayload(UUID body, List<Ray> rays) {
    this(body, rays, List.of());
  }

  public VisionDebugPayload {
    rays = List.copyOf(rays);
    targets = List.copyOf(targets);
    if (rays.size() > 416 || targets.size() > 128) {
      throw new IllegalArgumentException("Vision snapshot too large");
    }
  }

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
              b.writeBoolean(ray.hitBody() != null);
              if (ray.hitBody() != null) {
                b.writeUUID(ray.hitBody());
              }
            }
            b.writeVarInt(p.targets.size());
            for (var target : p.targets) {
              vector(b, target);
            }
          },
          b -> {
            var id = b.readUUID();
            int count = b.readVarInt();
            if (count < 0 || count > MAX_RAYS) {
              throw new IllegalArgumentException("Invalid vision sample count");
            }
            var rays = new ArrayList<Ray>();
            for (int i = 0; i < count; i++) {
              rays.add(
                  new Ray(
                      vector(b),
                      vector(b),
                      b.readBoolean(),
                      b.readBoolean(),
                      b.readBoolean() ? b.readUUID() : null));
            }
            int targetCount = b.readVarInt();
            if (targetCount < 0 || targetCount > 128) {
              throw new IllegalArgumentException("Invalid vision target count");
            }
            var targets = new ArrayList<Vec3>();
            for (int i = 0; i < targetCount; i++) {
              targets.add(vector(b));
            }
            return new VisionDebugPayload(id, List.copyOf(rays), targets);
          });

  public Type<VisionDebugPayload> type() {
    return TYPE;
  }
}
