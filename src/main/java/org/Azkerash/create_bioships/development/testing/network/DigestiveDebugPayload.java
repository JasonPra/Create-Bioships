package org.Azkerash.create_bioships.development.testing.network;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DigestiveDebugPayload(UUID body, List<Node> nodes, boolean truncated)
    implements CustomPacketPayload {
  public record Node(BlockPos pos, int kind) {}

  public static final int MAX_NODES = 4096;
  public static final Type<DigestiveDebugPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:digestive_debug"));
  public static final StreamCodec<RegistryFriendlyByteBuf, DigestiveDebugPayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            buffer.writeUUID(payload.body);
            buffer.writeBoolean(payload.truncated);
            buffer.writeVarInt(payload.nodes.size());
            for (var node : payload.nodes) {
              buffer.writeBlockPos(node.pos);
              buffer.writeByte(node.kind);
            }
          },
          buffer -> {
            var body = buffer.readUUID();
            boolean truncated = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_NODES)
              throw new IllegalArgumentException("Invalid graph size");
            var nodes = new ArrayList<Node>();
            for (int i = 0; i < count; i++)
              nodes.add(new Node(buffer.readBlockPos(), buffer.readUnsignedByte()));
            return new DigestiveDebugPayload(body, List.copyOf(nodes), truncated);
          });

  public Type<DigestiveDebugPayload> type() {
    return TYPE;
  }
}
