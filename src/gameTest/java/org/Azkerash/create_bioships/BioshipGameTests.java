package org.Azkerash.create_bioships;

import java.util.List;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.simulated.content.blocks.steering_wheel.*;
import net.minecraft.core.*;
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
        helper.assertTrue(food.getCount() == 3, "Dormant core consumed food"); helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "bioship_slider")
    public static void signedSliderRejectsInvalidAndDistantUpdates(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 5, 4));
        var level = helper.getLevel();
        level.setBlock(pos, Create_bioships.THROTTLE.get().defaultBlockState(), 3);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1);
        helper.assertTrue(BioshipNetwork.setThrottle(player, pos, 0), "Full reverse rejected");
        helper.assertTrue(LivingThrottleBlock.signed(level.getBlockState(pos)) == -1, "Reverse slider had wrong sign");
        helper.assertTrue(BioshipNetwork.setThrottle(player, pos, 20), "Full ahead rejected");
        helper.assertTrue(LivingThrottleBlock.signed(level.getBlockState(pos)) == 1, "Ahead slider had wrong sign");
        helper.assertTrue(BioshipNetwork.setThrottle(player, pos, 10), "Neutral rejected");
        helper.assertTrue(LivingThrottleBlock.signed(level.getBlockState(pos)) == 0, "Neutral did not stop thrust");
        helper.assertTrue(!BioshipNetwork.setThrottle(player, pos, -1) && !BioshipNetwork.setThrottle(player, pos, 21), "Out-of-range slider accepted");
        player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        helper.assertTrue(!BioshipNetwork.setThrottle(player, pos, 20), "Distant player changed throttle");
        helper.assertTrue(level.getBlockState(pos).getShape(level, pos).max(Direction.Axis.Y) <= 0.063, "Throttle is not a flat panel");
        helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "bioship_redstone")
    public static void altitudeNerveFollowsRedstone(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 5, 4));
        var nerve = Create_bioships.CONTROLS.stream().filter(c -> c.get().command == NerveControlBlock.Command.RISE).findFirst().orElseThrow().get();
        helper.getLevel().setBlock(pos, nerve.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(NerveControlBlock.ACTIVE), "Redstone did not activate altitude nerve");
        helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(NerveControlBlock.ACTIVE), "Unpowered nerve stayed active"); helper.succeed();
    }
    private static ServerSubLevel assemble(GameTestHelper helper) {
        // Motion is measured near the origin, independent of randomized test structure coordinates.
        BlockPos pos = new BlockPos(8, 80, 8); helper.getLevel().getChunk(0, 0);
        helper.getLevel().setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
        var ship = SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), pos, List.of(pos), new BoundingBox3i(pos, pos));
        SubLevelContainer.getContainer(helper.getLevel()).addForceLoadTicket(ship,
                dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
        return ship;
    }
    private static void cleanup(GameTestHelper helper, ServerSubLevel ship) {
        if (!ship.isRemoved()) SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }
    private static LivingCoreBlockEntity core(ServerSubLevel ship) {
        for (var actor : ship.getPlot().getBlockEntityActors()) if (actor instanceof LivingCoreBlockEntity core) return core;
        throw new IllegalStateException("No living core actor");
    }
    private static void feed(GameTestHelper helper, LivingCoreBlockEntity core) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var food = new ItemStack(Items.ROTTEN_FLESH, 64);
        for (int i = 0; i < 64; i++) core.interact(player, food);
        helper.assertTrue(food.isEmpty(), "Biomass not consumed");
        var mineral = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite")), 8);
        for (int i = 0; i < 8; i++) core.interact(player, mineral);
        helper.assertTrue(mineral.isEmpty(), "Levitite not accepted");
    }
    @GameTest(template = "test_empty", timeoutTicks = 1100, batch = "bioship_growth")
    public static void enclosedBodySwimsReversesAndUsesNativeHelm(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(600, () -> {
            try {
                var core = core(ship); var level = helper.getLevel();
                var saved = core.saveWithFullMetadata(level.registryAccess());
                helper.assertTrue(saved.getInt("Stage") == 2 && saved.getBoolean("Awakened"), "Larger body did not finish growing");
                for (var part : DinghyGrowth.body()) helper.assertTrue(level.getBlockState(core.getBlockPos().offset(part.offset())).is(part.state().getBlock()), "Missing part " + part.offset());
                helper.assertTrue(core.swimmingFlagella() == 6, "Body did not grow six functional flagella");
                helper.assertTrue(level.getBlockState(core.getBlockPos().offset(0, 4, 0)).is(Blocks.PURPLE_STAINED_GLASS), "Cabin has no roof");
                helper.assertTrue(level.getBlockState(core.getBlockPos().offset(1, 1, 0)).isAir(), "Strafe control still clutters the aisle");
                var helm = (SteeringWheelBlockEntity) level.getBlockEntity(core.getBlockPos().offset(DinghyGrowth.HELM));
                helper.assertTrue(helm != null && helm.getBlockState().getBlock() instanceof SteeringWheelBlock, "Helm does not use the native interaction/overlay");
                helper.assertTrue(helm.material.is(Blocks.CRIMSON_PLANKS), "Helm lost organic material");
                var restored = BlockEntity.loadStatic(core.getBlockPos(), core.getBlockState(), saved, level.registryAccess());
                helper.assertTrue(restored instanceof LivingCoreBlockEntity && restored.saveWithFullMetadata(level.registryAccess()).getInt("Stage") == 2, "Growth did not persist");
                var control = core.getBlockPos().offset(DinghyGrowth.THROTTLE);
                var captain = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
                var physicalPanel = ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(control));
                captain.setPos(physicalPanel.x, physicalPanel.y, physicalPanel.z + 1);
                helper.assertTrue(BioshipNetwork.setThrottle(captain, control, 20), "Slider update rejected on an assembled Sable ship");
                var start = new org.joml.Vector3d(ship.logicalPose().position());
                helper.runAfterDelay(40, () -> {
                    try {
                        helper.assertTrue(ship.logicalPose().position().z() < start.z() - 0.1, "Flagella did not propel body ahead");
                        helper.assertTrue(Math.abs(ship.logicalPose().position().y() - start.y()) < 3, "Lift failed");
                        level.setBlock(control, level.getBlockState(control).setValue(LivingThrottleBlock.THROTTLE, 0), 3);
                        helper.runAfterDelay(60, () -> {
                            try {
                                helper.assertTrue(ship.latestLinearVelocity.z() > 0.05, "Negative throttle did not reverse propulsion");
                                helm.targetAngleToUpdate = 90; helm.updateTargetAngle(90);
                                var heading = new org.joml.Quaterniond(ship.logicalPose().orientation());
                                helper.runAfterDelay(100, () -> {
                                    try {
                                        double angle = heading.invert().mul(ship.logicalPose().orientation()).angle();
                                        helper.assertTrue(Math.min(angle, Math.PI * 2 - angle) > 0.05, "Native helm did not turn the Sable body");
                                        for (BlockPos root : DinghyGrowth.FLAGELLA) level.setBlock(core.getBlockPos().offset(root), Blocks.AIR.defaultBlockState(), 3);
                                        helper.assertTrue(core.swimmingFlagella() == 0, "Destroyed flagella still supply propulsion");
                                        helper.succeed();
                                    } finally { cleanup(helper, ship); }
                                });
                            } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
                        });
                    } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
                });
            } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 1000, batch = "bioship_hover")
    public static void restingShipHoldsHeightAndCabinLightsPulse(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(600, () -> {
            try {
                var core = core(ship); var level = helper.getLevel();
                core.interact(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), ItemStack.EMPTY);
                double height = ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(core.getBlockPos())).y;
                BlockPos lamp = core.getBlockPos().offset(-2, 3, -2);
                boolean first = level.getBlockState(lamp).getValue(PulseLightBlock.LIT);
                final boolean[] changed = {false};
                for (int delay = 6; delay <= 30; delay += 6) helper.runAfterDelay(delay, () -> {
                    if (level.getBlockState(lamp).getValue(PulseLightBlock.LIT) != first) changed[0] = true;
                });
                helper.runAfterDelay(160, () -> {
                    try {
                        double after = ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(core.getBlockPos())).y;
                        helper.assertTrue(Math.abs(after - height) < 0.5, "Resting ship drifted away vertically: start=" + height + ", end=" + after + ", velocity=" + ship.latestLinearVelocity);
                        helper.assertTrue(changed[0], "Bioluminescent cabin lights did not pulse"); helper.succeed();
                    } finally { cleanup(helper, ship); }
                });
            } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 100, batch = "bioship_obstruction")
    public static void obstructedGrowthDoesNotOverwrite(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper); var core = core(ship);
        BlockPos obstruction = core.getBlockPos().offset(DinghyGrowth.body().getFirst().offset());
        helper.getLevel().setBlock(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        try { feed(helper, core); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(60, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK), "Growth overwrote an existing block");
                helper.assertTrue(core.saveWithFullMetadata(helper.getLevel().registryAccess()).getInt("Stage") == 0, "Obstructed growth started"); helper.succeed();
            } finally { cleanup(helper, ship); }
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 1000, batch = "bioship_mouth")
    public static void developedMouthConsumesCapturedPrey(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(600, () -> {
            try {
                var core = core(ship); var level = helper.getLevel();
                var saved = core.saveWithFullMetadata(level.registryAccess()); saved.putInt("Satiety", 100);
                core.loadAdditional(saved, level.registryAccess()); core.eatPrey();
                helper.assertTrue(core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") == 100, "Creature ate prey without a mouth");
                var extra = new ItemStack(Items.ROTTEN_FLESH, 32); var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                for (int n = 0; n < 32; n++) core.interact(player, extra);
                helper.assertTrue(extra.isEmpty(), "Mouth biomass was not consumed");
                helper.runAfterDelay(5, () -> {
                    try {
                        helper.assertTrue(core.hasMouth(), "Additional feeding did not develop a mouth");
                        var state = core.saveWithFullMetadata(level.registryAccess()); state.putInt("Satiety", 100); core.loadAdditional(state, level.registryAccess());
                        var chicken = net.minecraft.world.entity.EntityType.CHICKEN.create(level);
                        chicken.setNoAi(true); chicken.setNoGravity(true);
                        var food = core.mouthPosition(ship).add(0, -0.6, -0.3); chicken.setPos(food.x, food.y, food.z); level.addFreshEntity(chicken);
                        helper.runAfterDelay(100, () -> {
                            try {
                                helper.assertTrue(!chicken.isAlive(), "Tentacles/mouth did not attack and consume prey");
                                helper.assertTrue(core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") > 100, "Consumed prey did not nourish ship"); helper.succeed();
                            } finally { chicken.discard(); cleanup(helper, ship); }
                        });
                    } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
                });
            } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        });
    }
}
