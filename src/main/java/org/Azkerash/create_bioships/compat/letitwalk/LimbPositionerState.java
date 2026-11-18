package org.Azkerash.create_bioships.compat.letitwalk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LimbPositionerState(
    BlockPos pos,
    boolean compact,
    boolean assembled,
    int height,
    int angle,
    double stiffness,
    double damping)
    implements CustomPacketPayload {
  public static final Type<LimbPositionerState> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:limb_positioner_state"));
  public static final StreamCodec<RegistryFriendlyByteBuf, LimbPositionerState> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeBlockPos(p.pos);
            b.writeBoolean(p.compact);
            b.writeBoolean(p.assembled);
            b.writeVarInt(p.height);
            b.writeVarInt(p.angle);
            b.writeDouble(p.stiffness);
            b.writeDouble(p.damping);
          },
          b ->
              new LimbPositionerState(
                  b.readBlockPos(),
                  b.readBoolean(),
                  b.readBoolean(),
                  b.readVarInt(),
                  b.readVarInt(),
                  b.readDouble(),
                  b.readDouble()));

  @Override
  public Type<LimbPositionerState> type() {
    return TYPE;
  }
}
