package org.Azkerash.create_bioships.compat.letitwalk;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_bioships")
public record LimbPositionerPayload(BlockPos pos, int setting, int degrees)
    implements CustomPacketPayload {
  public static final Type<LimbPositionerPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("create_bioships", "limb_positioner"));
  public static final StreamCodec<ByteBuf, LimbPositionerPayload> CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          LimbPositionerPayload::pos,
          ByteBufCodecs.VAR_INT,
          LimbPositionerPayload::setting,
          ByteBufCodecs.VAR_INT,
          LimbPositionerPayload::degrees,
          LimbPositionerPayload::new);

  @Override
  public Type<LimbPositionerPayload> type() {
    return TYPE;
  }

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    event
        .registrar("2")
        .playToClient(
            LimbPositionerState.TYPE,
            LimbPositionerState.CODEC,
            (p, c) ->
                c.enqueueWork(
                    () ->
                        org.Azkerash.create_bioships.client.components.LimbPositionerScreen.receive(
                            p)));
    event
        .registrar("2")
        .playToServer(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () -> {
                      if (c.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        LimbPositionerActions.configure(player, p.pos, p.setting, p.degrees);
                      }
                    }));
  }
}
