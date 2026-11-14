package org.Azkerash.create_bioships;

import java.util.List;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public class BioshipGameTests {
    @GameTest(template = "test_empty", batch = "bioship_dormant")
    public static void dormantCoreDoesNotEat(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 5, 4));
        helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
        var core = (LivingCoreBlockEntity) helper.getLevel().getBlockEntity(pos);
        var food = new ItemStack(Items.ROTTEN_FLESH, 3);
        core.interact(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), food);
        helper.assertTrue(food.getCount() == 3, "Dormant core consumed food");
        helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "bioship_redstone")
    public static void nervePadFollowsRedstone(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 5, 4));
        var nerve = Create_bioships.CONTROLS.getFirst().get();
        helper.getLevel().setBlock(pos, nerve.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(NerveControlBlock.ACTIVE), "Redstone did not activate the nerve");
        helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(NerveControlBlock.ACTIVE), "Unpowered nerve stayed active");
        helper.getLevel().setBlock(pos, nerve.defaultBlockState().setValue(NerveControlBlock.LATCHED, true).setValue(NerveControlBlock.ACTIVE, true), 3);
        helper.getLevel().setBlock(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(NerveControlBlock.ACTIVE), "Redstone change cancelled a manually latched nerve");
        helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "bioship_helm")
    public static void physicalHelmAndThrottleAdjust(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 5, 4));
        var level = helper.getLevel();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var right = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.3, 0, 0), net.minecraft.core.Direction.SOUTH, pos, false);
        var left = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(-0.3, 0, 0), net.minecraft.core.Direction.SOUTH, pos, false);
        var throttle = Create_bioships.THROTTLE.get();
        level.setBlock(pos, throttle.defaultBlockState(), 3);
        for (int n = 0; n < 8; n++) throttle.useWithoutItem(level.getBlockState(pos), level, pos, player, right);
        helper.assertTrue(level.getBlockState(pos).getValue(LivingThrottleBlock.THROTTLE) == 5, "Throttle did not increase or exceeded maximum");
        throttle.useWithoutItem(level.getBlockState(pos), level, pos, player, left);
        helper.assertTrue(level.getBlockState(pos).getValue(LivingThrottleBlock.THROTTLE) == 4, "Throttle did not decrease");
        player.setShiftKeyDown(true);
        throttle.useWithoutItem(level.getBlockState(pos), level, pos, player, right);
        helper.assertTrue(level.getBlockState(pos).getValue(LivingThrottleBlock.THROTTLE) == 0, "Throttle did not stop");
        player.setShiftKeyDown(false);
        var helm = Create_bioships.HELM.get();
        level.setBlock(pos, helm.defaultBlockState(), 3);
        helm.useWithoutItem(level.getBlockState(pos), level, pos, player, left);
        helper.assertTrue(level.getBlockState(pos).getValue(LivingHelmBlock.STEERING) == 2, "Helm did not steer to port");
        helm.useWithoutItem(level.getBlockState(pos), level, pos, player, right);
        helm.useWithoutItem(level.getBlockState(pos), level, pos, player, right);
        helper.assertTrue(level.getBlockState(pos).getValue(LivingHelmBlock.STEERING) == 4, "Helm did not steer to starboard");
        player.setShiftKeyDown(true);
        helm.useWithoutItem(level.getBlockState(pos), level, pos, player, right);
        helper.assertTrue(level.getBlockState(pos).getValue(LivingHelmBlock.STEERING) == 3, "Helm did not straighten");
        helper.succeed();
    }
    private static ServerSubLevel assemble(GameTestHelper helper) {
        // Exercise motion near the origin, independently of the runner's randomized
        // structure placement near the world border (native physics loses precision there).
        BlockPos pos = new BlockPos(8, 80, 8);
        helper.getLevel().getChunk(0, 0);
        helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
        var ship = SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), pos, List.of(pos), new BoundingBox3i(pos, pos));
        // GameTest runs without real players, so explicitly keep the test organism loaded.
        SubLevelContainer.getContainer(helper.getLevel()).addForceLoadTicket(ship,
            dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
        return ship;
    }
    private static LivingCoreBlockEntity core(ServerSubLevel ship) {
        for (var actor : ship.getPlot().getBlockEntityActors()) if (actor instanceof LivingCoreBlockEntity core) return core;
        throw new IllegalStateException("Assembled core was not registered as a Sable actor");
    }
    private static void feed(GameTestHelper helper, LivingCoreBlockEntity core) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var food = new ItemStack(Items.ROTTEN_FLESH, 64);
        for (int i = 0; i < 64; i++) core.interact(player, food);
        helper.assertTrue(food.isEmpty(), "Biomass was not consumed");
        var mineral = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite")), 8);
        for (int i = 0; i < 8; i++) core.interact(player, mineral);
        helper.assertTrue(mineral.isEmpty(), "Aeronautics levitite was not accepted");
    }
    @GameTest(template = "test_empty", timeoutTicks = 600, batch = "bioship_growth")
    public static void assembledCoreGrowsAndPersists(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); }
        catch (Throwable failure) { if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED); throw failure; }
        helper.runAtTickTime(400, () -> {
            try {
                var core = core(ship);
                var saved = core.saveWithFullMetadata(helper.getLevel().registryAccess());
                helper.assertTrue(saved.getBoolean("Awakened"), "Core did not awaken");
                helper.assertTrue(saved.getInt("Stage") == 2, "Dinghy did not finish growing");
                helper.assertTrue(saved.getInt("Biomass") == 64 && saved.getInt("Levitite") == 8, "Material accounting changed");
                for (var part : DinghyGrowth.body())
                    helper.assertTrue(helper.getLevel().getBlockState(core.getBlockPos().offset(part.offset())).equals(part.state()), "Missing body part " + part.offset());
                var restored = BlockEntity.loadStatic(core.getBlockPos(), core.getBlockState(), saved, helper.getLevel().registryAccess());
                helper.assertTrue(restored instanceof LivingCoreBlockEntity, "Core did not deserialize");
                var roundTrip = restored.saveWithFullMetadata(helper.getLevel().registryAccess());
                helper.assertTrue(roundTrip.getInt("Stage") == 2 && roundTrip.getInt("Satiety") == saved.getInt("Satiety"), "Needs or growth lost on reload");
                // Physical control state is saved with the structure and the core can rest it.
                var controlPos = core.getBlockPos().offset(-1, 1, 1);
                helper.getLevel().setBlock(controlPos, Create_bioships.THROTTLE.get().defaultBlockState().setValue(LivingThrottleBlock.THROTTLE, 5), 3);
                core.interact(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), ItemStack.EMPTY);
                helper.assertTrue(core.saveWithFullMetadata(helper.getLevel().registryAccess()).getBoolean("Resting"), "Core did not pause the physical controls");
                core.interact(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), ItemStack.EMPTY);
                helper.assertTrue(!core.saveWithFullMetadata(helper.getLevel().registryAccess()).getBoolean("Resting"), "Core did not resume the physical controls");
                var start = new org.joml.Vector3d(ship.logicalPose().position());
                helper.runAfterDelay(40, () -> {
                    try {
                        helper.assertTrue(ship.logicalPose().position().z() < start.z() - 0.1,
                                "The physical throttle did not propel its Sable body");
                        helper.assertTrue(Math.abs(ship.logicalPose().position().y() - start.y()) < 3,
                                "The ship did not retain lift while moving");
                        var originalHeading = new org.joml.Quaterniond(ship.logicalPose().orientation());
                        helper.getLevel().setBlock(core.getBlockPos().offset(0, 1, -1),
                                Create_bioships.HELM.get().defaultBlockState().setValue(LivingHelmBlock.STEERING, 0), 3);
                        helper.runAfterDelay(40, () -> {
                            try {
                                double angle = originalHeading.invert().mul(ship.logicalPose().orientation()).angle();
                                helper.assertTrue(Math.min(angle, Math.PI * 2 - angle) > 0.05,
                                        "The physical helm did not turn its Sable body");
                                helper.succeed();
                            } finally { if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED); }
                        });
                    } catch (Throwable failure) {
                        if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                        throw failure;
                    }
                });
            } catch (Throwable failure) {
                if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                throw failure;
            }
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 100, batch = "bioship_obstruction")
    public static void obstructedGrowthDoesNotOverwrite(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        var core = core(ship);
        BlockPos obstruction = core.getBlockPos().offset(DinghyGrowth.body().getFirst().offset());
        helper.getLevel().setBlock(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        try { feed(helper, core); }
        catch (Throwable failure) { if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED); throw failure; }
        helper.runAtTickTime(60, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK), "Growth overwrote an existing block");
                helper.assertTrue(core.saveWithFullMetadata(helper.getLevel().registryAccess()).getInt("Stage") == 0, "Obstructed growth spent its reserved materials");
                helper.succeed();
            } finally { if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED); }
        });
    }
}
