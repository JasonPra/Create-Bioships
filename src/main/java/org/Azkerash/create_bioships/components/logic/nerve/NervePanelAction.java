package org.Azkerash.create_bioships.components.logic.nerve;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NervePanelAction(UUID grid, BlockPos panel, int action, UUID target)
    implements CustomPacketPayload {
  public static final Type<NervePanelAction> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:nerve_action"));
  public static final StreamCodec<RegistryFriendlyByteBuf, NervePanelAction> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.grid);
            b.writeBlockPos(p.panel);
            b.writeByte(p.action);
            b.writeUUID(p.target);
          },
          b -> {
            var id = b.readUUID();
            var pos = b.readBlockPos();
            int action = b.readUnsignedByte();
            if (action > 3) throw new IllegalArgumentException("Invalid nerve action");
            return new NervePanelAction(id, pos, action, b.readUUID());
          });

  public Type<NervePanelAction> type() {
    return TYPE;
  }
}
