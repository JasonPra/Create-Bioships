package org.azkerash.createbioships.development.testing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PathDebugRequest(boolean enabled) implements CustomPacketPayload {
  public static final Type<PathDebugRequest> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:path_debug_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PathDebugRequest> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeBoolean(p.enabled), b -> new PathDebugRequest(b.readBoolean()));

  public Type<PathDebugRequest> type() {
    return TYPE;
  }
}
