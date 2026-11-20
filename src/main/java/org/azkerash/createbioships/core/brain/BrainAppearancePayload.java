package org.azkerash.createbioships.core.brain;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Topology-only snapshot. Brain programs remain exclusively on the owning grid. */
@EventBusSubscriber(modid = "create_bioships")
public record BrainAppearancePayload(
    UUID body, List<BlockPos> cells, BlockPos port, Direction face, boolean alive)
    implements CustomPacketPayload {
  public static final Type<BrainAppearancePayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:brain_appearance"));
  public static final StreamCodec<RegistryFriendlyByteBuf, BrainAppearancePayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeUUID(p.body);
            b.writeVarInt(p.cells.size());
            for (var cell : p.cells) {
              b.writeBlockPos(cell);
            }
            b.writeBlockPos(p.port == null ? BlockPos.ZERO : p.port);
            b.writeEnum(p.face);
            b.writeBoolean(p.alive);
          },
          b -> {
            var id = b.readUUID();
            int count = b.readVarInt();
            if (count < 0 || count > 27) {
              throw new IllegalArgumentException("Brain size");
            }
            var cells = new ArrayList<BlockPos>();
            for (int i = 0; i < count; i++) {
              cells.add(b.readBlockPos());
            }
            return new BrainAppearancePayload(
                id,
                List.copyOf(cells),
                b.readBlockPos(),
                b.readEnum(Direction.class),
                b.readBoolean());
          });

  @Override
  public Type<BrainAppearancePayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () ->
                        org.azkerash.createbioships.client.components.BrainSurfaceRenderer.receive(
                            p)));
  }
}
