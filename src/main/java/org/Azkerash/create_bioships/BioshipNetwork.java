package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BioshipNetwork {
  public record Throttle(BlockPos pos, int setting) implements CustomPacketPayload {
    public static final Type<Throttle> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "throttle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Throttle> CODEC =
        StreamCodec.of(
            (buf, p) -> {
              buf.writeBlockPos(p.pos);
              buf.writeVarInt(p.setting);
            },
            buf -> new Throttle(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public Type<Throttle> type() {
      return TYPE;
    }
  }

  public record SightRequest() implements CustomPacketPayload {
    public static final Type<SightRequest> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "sight_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SightRequest> CODEC =
        StreamCodec.unit(new SightRequest());

    @Override
    public Type<SightRequest> type() {
      return TYPE;
    }
  }

  public record SightSnapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload {
    public static final Type<SightSnapshot> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "sight_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SightSnapshot> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeNbt(p.data),
            buf -> new SightSnapshot(java.util.Objects.requireNonNull(buf.readNbt())));

    @Override
    public Type<SightSnapshot> type() {
      return TYPE;
    }
  }

  public record PathRequest() implements CustomPacketPayload {
    public static final Type<PathRequest> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "path_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathRequest> CODEC =
        StreamCodec.unit(new PathRequest());

    @Override
    public Type<PathRequest> type() {
      return TYPE;
    }
  }

  public record PathSnapshot(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload {
    public static final Type<PathSnapshot> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "path_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathSnapshot> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeNbt(p.data),
            buf -> new PathSnapshot(java.util.Objects.requireNonNull(buf.readNbt())));

    @Override
    public Type<PathSnapshot> type() {
      return TYPE;
    }
  }

  public record TrustBurst(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload {
    public static final Type<TrustBurst> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "trust_burst"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrustBurst> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeNbt(p.data),
            buf -> new TrustBurst(java.util.Objects.requireNonNull(buf.readNbt())));

    @Override
    public Type<TrustBurst> type() {
      return TYPE;
    }
  }

  public record Voice(net.minecraft.nbt.CompoundTag data) implements CustomPacketPayload {
    public static final Type<Voice> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "voice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Voice> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeNbt(p.data),
            buf -> new Voice(java.util.Objects.requireNonNull(buf.readNbt())));

    @Override
    public Type<Voice> type() {
      return TYPE;
    }
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("6");
    registrar.playToClient(
        Voice.TYPE,
        Voice.CODEC,
        (payload, context) -> {
          var persistent = context.player().getPersistentData();
          var inbox = persistent.getList("BioshipVoices", net.minecraft.nbt.Tag.TAG_COMPOUND);
          if (inbox.size() < 32) {
            inbox.add(payload.data());
            persistent.put("BioshipVoices", inbox);
          }
        });
    registrar.playToClient(
        TrustBurst.TYPE,
        TrustBurst.CODEC,
        (payload, context) -> {
          var persistent = context.player().getPersistentData();
          var inbox =
              persistent.getList(BioshipTrustFeedback.INBOX, net.minecraft.nbt.Tag.TAG_COMPOUND);
          if (inbox.size() < 32) {
            var data = payload.data().copy();
            data.putLong("Received", System.nanoTime());
            inbox.add(data);
            persistent.put(BioshipTrustFeedback.INBOX, inbox);
          }
        });
    registrar.playToServer(
        PathRequest.TYPE,
        PathRequest.CODEC,
        (payload, context) -> {
          if (context.player() instanceof ServerPlayer player) {
            var result = NavigationDebug.request(player);
            if (result != null) context.reply(new PathSnapshot(result));
          }
        });
    registrar.playToClient(
        PathSnapshot.TYPE,
        PathSnapshot.CODEC,
        (payload, context) -> {
          context.player().getPersistentData().put(NavigationDebug.DATA, payload.data());
          context.player().getPersistentData().putInt("PathReceived", context.player().tickCount);
        });
    registrar.playToServer(
        SightRequest.TYPE,
        SightRequest.CODEC,
        (payload, context) -> {
          if (context.player() instanceof ServerPlayer player) {
            var result = VisionDebug.request(player);
            if (result != null) context.reply(new SightSnapshot(result));
          }
        });
    registrar.playToClient(
        SightSnapshot.TYPE,
        SightSnapshot.CODEC,
        (payload, context) -> {
          context.player().getPersistentData().put(VisionDebug.DATA, payload.data());
          context.player().getPersistentData().putInt("SightReceived", context.player().tickCount);
        });
    registrar.playToServer(
        Throttle.TYPE,
        Throttle.CODEC,
        (payload, context) -> {
          if (context.player() instanceof ServerPlayer player)
            setThrottle(player, payload.pos(), payload.setting());
        });
  }

  public static boolean setThrottle(ServerPlayer player, BlockPos pos, int setting) {
    if (!AirshipPrototype.available()
        || setting < 0
        || setting > 20
        || player.isSpectator()
        || !player.serverLevel().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
      return false;
    var state = player.serverLevel().getBlockState(pos);
    if (!(state.getBlock() instanceof LivingThrottleBlock)) return false;
    Vec3 physical = Vec3.atCenterOf(pos);
    var container = SubLevelContainer.getContainer(player.serverLevel());
    if (container != null && container.inBounds(pos)) {
      var plot = container.getPlot(new ChunkPos(pos));
      if (plot == null) return false;
      physical = plot.getSubLevel().logicalPose().transformPosition(physical);
    }
    double reach = player.blockInteractionRange() + 1;
    if (player.getEyePosition().distanceToSqr(physical) > reach * reach) return false;
    var core = BioshipAccess.core(player.serverLevel(), pos);
    if (core != null) core.autonomy().command(core, player);
    return player
        .serverLevel()
        .setBlock(pos, state.setValue(LivingThrottleBlock.THROTTLE, setting), 3);
  }
}
