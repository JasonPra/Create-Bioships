package org.Azkerash.create_bioships.development.testing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreatureStatsRequest(boolean enabled) implements CustomPacketPayload {
  public static final Type<CreatureStatsRequest> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:creature_stats_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CreatureStatsRequest> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeBoolean(p.enabled), b -> new CreatureStatsRequest(b.readBoolean()));

  public Type<CreatureStatsRequest> type() {
    return TYPE;
  }
}
