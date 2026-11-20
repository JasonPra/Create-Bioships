package org.azkerash.createbioships.compat.biomancy.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.azkerash.createbioships.core.access.SubLevelPermissions;

public record MembraneAccessPayload(UUID grid, int mode, List<UUID> allowed)
    implements CustomPacketPayload {
  public static final Type<MembraneAccessPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:membrane_access"));
  public static final StreamCodec<RegistryFriendlyByteBuf, MembraneAccessPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.grid);
            b.writeByte(p.mode);
            b.writeVarInt(p.allowed.size());
            for (var id : p.allowed) {
              b.writeUUID(id);
            }
          },
          b -> {
            var id = b.readUUID();
            int mode = b.readUnsignedByte(), count = b.readVarInt();
            if (mode > 2 || count < 0 || count > SubLevelPermissions.MAX_ENTRIES + 1) {
              throw new IllegalArgumentException("Invalid membrane access snapshot");
            }
            var allowed = new ArrayList<UUID>();
            for (int i = 0; i < count; i++) {
              allowed.add(b.readUUID());
            }
            return new MembraneAccessPayload(id, mode, List.copyOf(allowed));
          });

  public Type<MembraneAccessPayload> type() {
    return TYPE;
  }
}
