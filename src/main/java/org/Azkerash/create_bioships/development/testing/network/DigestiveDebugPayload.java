package org.Azkerash.create_bioships.development.testing.network;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DigestiveDebugPayload(UUID body, List<Node> nodes, boolean truncated)
    implements CustomPacketPayload {
  public record ItemTotal(ResourceLocation item, int count) {
    public ItemTotal {
      if (count <= 0) throw new IllegalArgumentException("Invalid item total");
    }
  }

  public record Node(
      BlockPos pos, int kind, int slots, int used, int items, List<ItemTotal> contents) {
    public Node {
      contents = List.copyOf(contents);
      if (contents.size() > 27) throw new IllegalArgumentException("Too many inventory item types");
    }

    public Node(BlockPos pos, int kind, int slots, int used, int items) {
      this(pos, kind, slots, used, items, List.of());
    }

    public Node(BlockPos pos, int kind) {
      this(pos, kind, 0, 0, 0);
    }
  }

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
              buffer.writeVarInt(node.slots);
              buffer.writeVarInt(node.used);
              buffer.writeVarInt(node.items);
              buffer.writeVarInt(node.contents.size());
              for (var item : node.contents) {
                buffer.writeVarInt(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item.item())));
                buffer.writeVarInt(item.count());
              }
            }
          },
          buffer -> {
            var body = buffer.readUUID();
            boolean truncated = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_NODES)
              throw new IllegalArgumentException("Invalid graph size");
            var nodes = new ArrayList<Node>();
            for (int i = 0; i < count; i++) {
              var pos = buffer.readBlockPos();
              int kind = buffer.readUnsignedByte(),
                  slots = buffer.readVarInt(),
                  used = buffer.readVarInt(),
                  items = buffer.readVarInt();
              int types = buffer.readVarInt();
              if (types < 0 || types > 27)
                throw new IllegalArgumentException("Invalid inventory summary");
              var contents = new ArrayList<ItemTotal>();
              for (int j = 0; j < types; j++) contents.add(readItem(buffer));
              nodes.add(new Node(pos, kind, slots, used, items, contents));
            }
            return new DigestiveDebugPayload(body, List.copyOf(nodes), truncated);
          });

  private static ItemTotal readItem(RegistryFriendlyByteBuf buffer) {
    int id = buffer.readVarInt();
    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id);
    if (id < 0 || item == null || item == net.minecraft.world.item.Items.AIR)
      throw new IllegalArgumentException("Invalid inventory item");
    return new ItemTotal(
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item), buffer.readVarInt());
  }

  public Type<DigestiveDebugPayload> type() {
    return TYPE;
  }
}
