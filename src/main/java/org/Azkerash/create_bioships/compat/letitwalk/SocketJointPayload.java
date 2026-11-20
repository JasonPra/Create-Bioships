package org.Azkerash.create_bioships.compat.letitwalk;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "create_bioships")
public record SocketJointPayload(BlockPos pos, int joint, int setting, int delta)
    implements CustomPacketPayload {
  public static final Type<SocketJointPayload> TYPE =
      new Type<>(ResourceLocation.parse("create_bioships:socket_joint"));
  public static final StreamCodec<ByteBuf, SocketJointPayload> CODEC =
      StreamCodec.of(
          (b, p) -> {
            b.writeLong(p.pos.asLong());
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(b, p.joint);
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(b, p.setting);
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(b, p.delta);
          },
          b ->
              new SocketJointPayload(
                  BlockPos.of(b.readLong()),
                  net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(b),
                  net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(b),
                  net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(b)));

  @Override
  public Type<SocketJointPayload> type() {
    return TYPE;
  }

  public record State(BlockPos pos, int joint, boolean compact, int swivel, int bend)
      implements CustomPacketPayload {
    public static final Type<State> TYPE =
        new Type<>(ResourceLocation.parse("create_bioships:socket_joint_state"));
    public static final StreamCodec<ByteBuf, State> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeLong(p.pos.asLong());
              b.writeInt(p.joint);
              b.writeBoolean(p.compact);
              b.writeInt(p.swivel);
              b.writeInt(p.bend);
            },
            b ->
                new State(
                    BlockPos.of(b.readLong()),
                    b.readInt(),
                    b.readBoolean(),
                    b.readInt(),
                    b.readInt()));

    @Override
    public Type<State> type() {
      return TYPE;
    }
  }

  @SubscribeEvent
  public static void register(
      net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToServer(
            TYPE,
            CODEC,
            (p, c) ->
                c.enqueueWork(
                    () -> {
                      if (c.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        SocketJointActions.configure(player, p.pos, p.joint, p.setting, p.delta);
                      }
                    }));
    event
        .registrar("1")
        .playToClient(
            State.TYPE,
            State.CODEC,
            (p, c) ->
                c.enqueueWork(
                    () ->
                        org.Azkerash.create_bioships.client.components.SocketJointScreen.receive(
                            p)));
  }
}
