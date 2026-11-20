package org.azkerash.createbioships.development.testing.network;

import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record FacingDebugPayload(UUID body, Direction front, Vec3 origin, boolean visible)
    implements CustomPacketPayload {
  public FacingDebugPayload {
    if (front.getAxis() == Direction.Axis.Y
        || !Double.isFinite(origin.x)
        || !Double.isFinite(origin.y)
        || !Double.isFinite(origin.z)) {
      throw new IllegalArgumentException("Invalid facing snapshot");
    }
  }

  public static final Type<FacingDebugPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:facing_debug"));
  public static final StreamCodec<RegistryFriendlyByteBuf, FacingDebugPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeEnum(p.front);
            b.writeDouble(p.origin.x);
            b.writeDouble(p.origin.y);
            b.writeDouble(p.origin.z);
            b.writeBoolean(p.visible);
          },
          b ->
              new FacingDebugPayload(
                  b.readUUID(),
                  b.readEnum(Direction.class),
                  new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                  b.readBoolean()));

  public Type<FacingDebugPayload> type() {
    return TYPE;
  }
}
