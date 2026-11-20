package org.azkerash.createbioships.development.testing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FacingDebugRequest(boolean enabled) implements CustomPacketPayload {
  public static final Type<FacingDebugRequest> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:facing_debug_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, FacingDebugRequest> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeBoolean(p.enabled), b -> new FacingDebugRequest(b.readBoolean()));

  public Type<FacingDebugRequest> type() {
    return TYPE;
  }
}
