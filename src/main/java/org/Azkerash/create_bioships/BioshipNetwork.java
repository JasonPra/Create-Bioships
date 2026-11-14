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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BioshipNetwork {
    public record Throttle(BlockPos pos, int setting) implements CustomPacketPayload {
        public static final Type<Throttle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "throttle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Throttle> CODEC = StreamCodec.of((buf, p) -> { buf.writeBlockPos(p.pos); buf.writeVarInt(p.setting); }, buf -> new Throttle(buf.readBlockPos(), buf.readVarInt()));
        @Override public Type<Throttle> type() { return TYPE; }
    }
    public record OpenThrottle(BlockPos pos, int setting) implements CustomPacketPayload {
        public static final Type<OpenThrottle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "open_throttle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenThrottle> CODEC = StreamCodec.of((buf, p) -> { buf.writeBlockPos(p.pos); buf.writeVarInt(p.setting); }, buf -> new OpenThrottle(buf.readBlockPos(), buf.readVarInt()));
        @Override public Type<OpenThrottle> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Throttle.TYPE, Throttle.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) setThrottle(player, payload.pos(), payload.setting());
        });
        registrar.playToClient(OpenThrottle.TYPE, OpenThrottle.CODEC, (payload, context) ->
            org.Azkerash.create_bioships.client.BioshipClient.openThrottle(payload.pos(), payload.setting()));
    }
    public static boolean setThrottle(ServerPlayer player, BlockPos pos, int setting) {
        if (setting < 0 || setting > 20 || player.isSpectator() || !player.serverLevel().hasChunkAt(pos)) return false;
        var state = player.serverLevel().getBlockState(pos);
        if (!state.is(Create_bioships.THROTTLE.get())) return false;
        Vec3 physical = Vec3.atCenterOf(pos);
        var container = SubLevelContainer.getContainer(player.serverLevel());
        if (container != null && container.inBounds(pos)) {
            var plot = container.getPlot(new ChunkPos(pos));
            if (plot == null) return false;
            physical = plot.getSubLevel().logicalPose().transformPosition(physical);
        }
        double reach = player.blockInteractionRange() + 1;
        if (player.getEyePosition().distanceToSqr(physical) > reach * reach) return false;
        return player.serverLevel().setBlock(pos, state.setValue(LivingThrottleBlock.THROTTLE, setting), 3);
    }
    public static void openThrottle(ServerPlayer player, BlockPos pos, int setting) { PacketDistributor.sendToPlayer(player, new OpenThrottle(pos, setting)); }
}
