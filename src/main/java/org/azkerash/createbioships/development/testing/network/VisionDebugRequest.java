package org.azkerash.createbioships.development.testing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VisionDebugRequest(boolean enabled) implements CustomPacketPayload {
  public static final Type<VisionDebugRequest> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:vision_debug_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, VisionDebugRequest> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeBoolean(p.enabled), b -> new VisionDebugRequest(b.readBoolean()));

  public Type<VisionDebugRequest> type() {
    return TYPE;
  }
}
