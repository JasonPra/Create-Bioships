package org.azkerash.createbioships.development.testing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DigestiveDebugRequest(boolean enabled) implements CustomPacketPayload {
  public static final Type<DigestiveDebugRequest> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:digestive_debug_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, DigestiveDebugRequest> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeBoolean(p.enabled), b -> new DigestiveDebugRequest(b.readBoolean()));

  public Type<DigestiveDebugRequest> type() {
    return TYPE;
  }
}
