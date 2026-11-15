package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

/** World mutation happens in ordinary block ticks, outside Sable's actor iteration. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class BioshipIntegration {
    private static final String BOUND = "BioshipBound";
    private static final TagKey<net.minecraft.world.level.block.Block> MACHINES = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "assimilable_machines"));
    private static final TagKey<net.minecraft.world.level.block.Block> FLESH = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "construction_flesh"));
    private static final TagKey<Item> MATERIAL = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "construction_biomass"));
    public static void bind(ServerSubLevel ship) {
        var tag = ship.getUserDataTag();
        if (tag == null) tag = new net.minecraft.nbt.CompoundTag();
        if (!tag.getBoolean(BOUND)) { tag.putBoolean(BOUND, true); ship.setUserDataTag(tag); }
    }
    public static boolean bound(SubLevel ship) {
        if (!(ship instanceof ServerSubLevel server)) return false;
        if (server.getUserDataTag() != null && server.getUserDataTag().getBoolean(BOUND)) return true;
        for (var actor : server.getPlot().getBlockEntityActors()) if (actor instanceof LivingCoreBlockEntity) return true;
        return false;
    }
    public static ServerSubLevel containing(Level level, BlockPos pos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) return null;
        var plot = container.getPlot(new net.minecraft.world.level.ChunkPos(pos));
        return plot != null && plot.getSubLevel() instanceof ServerSubLevel server ? server : null;
    }
    public static void awaken(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Create_bioships.CORE.get()) || containing(level, pos) != null) return;
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos), new BoundingBox3i(pos, pos));
        if (ship != null) bind(ship);
    }
    public static boolean isMaw(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(ResourceLocation.fromNamespaceAndPath("biomancy", "maw_hopper"));
    }
    public static boolean isIntegratedMachine(Level level, BlockPos pos) { return level.getBlockState(pos).is(MACHINES); }
    public static List<net.minecraft.world.level.block.entity.BlockEntity> blockEntities(ServerSubLevel ship) {
        List<net.minecraft.world.level.block.entity.BlockEntity> result = new ArrayList<>();
        for (var chunk : ship.getPlot().getLoadedChunks()) result.addAll(chunk.getChunk().getBlockEntities().values());
        return result;
    }
    public static int countBlocks(ServerSubLevel ship) {
        int count = 0;
        for (var chunk : ship.getPlot().getLoadedChunks()) for (var section : chunk.getChunk().getSections()) {
            if (section.hasOnlyAir()) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                if (!section.getBlockState(x, y, z).isAir()) count++;
        }
        return count;
    }
    public static void assimilate(LivingCoreBlockEntity core, ServerSubLevel ship) {
        ServerLevel level = ship.getLevel();
        // Bounded three-block neighborhood of core, then one-block neighborhood of each existing organ/machine.
        Set<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos physical = BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(core.getBlockPos())));
        BlockPos.betweenClosed(physical.offset(-3, 0, -3), physical.offset(3, 3, 3)).forEach(p -> candidates.add(p.immutable()));
        for (var actor : blockEntities(ship)) {
            BlockPos point = BlockPos.containing(ship.logicalPose().transformPosition(Vec3.atCenterOf(actor.getBlockPos())));
            for (Direction direction : Direction.values()) candidates.add(point.relative(direction));
            consumeStoredBiomass(core, actor.getBlockPos());
        }
        int moved = 0;
        for (BlockPos source : candidates) {
            if (moved >= 16 || !level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4) || containing(level, source) != null) continue;
            BlockState state = level.getBlockState(source);
            if (state.is(FLESH) && core.missingBiomass() > 0) {
                level.removeBlock(source, false); core.incorporateBiomass(1); moved++; continue;
            }
            if (!state.is(MACHINES)) continue;
            BlockPos target = BlockPos.containing(ship.logicalPose().transformPositionInverse(Vec3.atCenterOf(source)));
            if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(target)) || !level.isInWorldBounds(target) || !level.getBlockState(target).isAir()) continue;
            boolean attached = false;
            for (Direction direction : Direction.values()) if (!level.getBlockState(target.relative(direction)).isAir()) attached = true;
            if (!attached) {
                target = null;
                for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
                    BlockPos port = core.getBlockPos().relative(direction);
                    if (level.getBlockState(port).isAir()) { target = port; break; }
                }
                if (target == null) continue;
            }
            var transform = new SubLevelAssemblyHelper.AssemblyTransform(source, target, 0, Rotation.NONE, level);
            SubLevelAssemblyHelper.moveBlocks(level, transform, List.of(source));
            consumeStoredBiomass(core, target); moved++;
        }
    }
    private static void consumeStoredBiomass(LivingCoreBlockEntity core, BlockPos pos) {
        if (!isIntegratedMachine(core.getLevel(), pos) || core.missingBiomass() == 0) return;
        var inventory = core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSlots() && core.missingBiomass() > 0; slot++) {
            if (!inventory.getStackInSlot(slot).is(MATERIAL)) continue;
            var taken = inventory.extractItem(slot, core.missingBiomass(), false);
            core.incorporateBiomass(taken.getCount());
        }
    }
    @SubscribeEvent public static void feedMaw(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !isMaw(event.getLevel().getBlockState(event.getPos()))) return;
        var ship = containing(event.getLevel(), event.getPos());
        if (ship == null) return;
        for (var actor : ship.getPlot().getBlockEntityActors()) if (actor instanceof LivingCoreBlockEntity core) {
            if (core.feedAtMouth(event.getEntity(), event.getItemStack())) {
                event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
            return;
        }
    }
}
