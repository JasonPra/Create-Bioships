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
        helper.assertTrue(food.getCount() == 3, "Dormant core consumed food");
        helper.assertTrue(!levelState(helper, pos).getValue(LivingCoreBlock.ACTIVE) && !levelState(helper, pos).getValue(LivingCoreBlock.BLINK), "Dormant eye blinked");
        helper.getLevel().removeBlock(pos, false); helper.succeed();
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
        helper.assertTrue(level.getBlockState(pos).getShape(level, pos).max(Direction.Axis.Y) <= 1, "Physical tendon exceeds its block");
        helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "bioship_trim")
    public static void verticalTrimCombinesAscentDescentAndNeutral(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(4, 5, 4));
        level.setBlock(pos, Create_bioships.VERTICAL_TRIM.get().defaultBlockState(), 3);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1);
        for (int setting : new int[]{0, 20, 10}) {
            helper.assertTrue(BioshipNetwork.setThrottle(player, pos, setting), "Trim setting rejected");
            helper.assertTrue(LivingThrottleBlock.signed(level.getBlockState(pos)) == (setting - 10) / 10.0, "Wrong trim sign");
        }
        helper.succeed();
    }
    private static net.minecraft.world.level.block.state.BlockState levelState(GameTestHelper helper, BlockPos pos) { return helper.getLevel().getBlockState(pos); }
    private static net.minecraft.world.entity.player.Player playerForPlacement(GameTestHelper helper) { return helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); }
    private static int nextFixture;
    private static BlockPos fixturePosition() { return new BlockPos(8 + 64 * nextFixture++, 80, 8); }
    private static ServerSubLevel assemble(GameTestHelper helper) {
        // Motion is measured near the origin, independent of randomized test structure coordinates.
        BlockPos pos = fixturePosition(); helper.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        for (BlockPos ground : BlockPos.betweenClosed(pos.offset(-16, -1, -16), pos.offset(16, -1, 16)))
            helper.getLevel().setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
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
        helper.getLevel().setBlock(core.getBlockPos().offset(0, 0, -1), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("biomancy", "maw_hopper")).defaultBlockState(), 3);
        var food = new ItemStack(Items.BEEF, 64);
        for (int i = 0; i < 64; i++) core.feedAtMouth(player, food);
        helper.assertTrue(food.isEmpty(), "Biomass not consumed");
    }
    private static String describeShips(GameTestHelper helper) {
        StringBuilder detail = new StringBuilder();
        for (var ship : SubLevelContainer.getContainer(helper.getLevel()).getAllSubLevels()) if (ship instanceof ServerSubLevel server) {
            detail.append(" ship ").append(ship.logicalPose()).append(" blocks ");
            for (var block : BioshipIntegration.blockEntities(server)) detail.append(block.getBlockPos()).append("=").append(block.getBlockState()).append("; ");
        }
        String result = detail.toString(); return result.length() > 800 ? result.substring(0, 800) : result;
    }
    private static String growthObstruction(GameTestHelper helper, LivingCoreBlockEntity core, ServerSubLevel ship) {
        for (var part : DinghyGrowth.body()) {
            BlockPos logical = core.getBlockPos().offset(part.offset());
            if (BioshipIntegration.isIntegratedMachine(helper.getLevel(), logical)) continue;
            BlockPos physical = BlockPos.containing(ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(logical)));
            var state = helper.getLevel().getBlockState(physical);
            if (!state.isAir()) return " | obstructed at " + part.offset() + " physical " + physical + " by " + state + " pose " + ship.logicalPose();
        }
        return " | no physical obstruction";
    }
    private static void enableFlight(GameTestHelper helper, LivingCoreBlockEntity core) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var mineral = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite_blend_bucket")), 8);
        for (int i = 0; i < 8; i++) helper.assertTrue(core.feedAtMouth(player, mineral), "Dose rejected at " + i + "; mouth=" + core.hasMouth() + "; ship=" + core.ship() + "; item=" + mineral + "; state=" + core.saveWithFullMetadata(helper.getLevel().registryAccess()));
        helper.assertTrue(mineral.isEmpty(), "Amalgam blend not accepted: " + describeShips(helper));
        var trim = core.getBlockPos().offset(DinghyGrowth.VERTICAL_TRIM);
        helper.getLevel().setBlock(trim, helper.getLevel().getBlockState(trim).setValue(LivingThrottleBlock.THROTTLE, 20), 3);
    }
    @GameTest(template = "test_empty", timeoutTicks = 1400, batch = "bioship_growth")
    public static void enclosedBodySwimsReversesAndUsesNativeHelm(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(800, () -> {
            try {
                var core = core(ship); var level = helper.getLevel();
                var saved = core.saveWithFullMetadata(level.registryAccess());
                helper.assertTrue(saved.getInt("Stage") == 2 && saved.getBoolean("Awakened"), "Larger body did not finish growing: " + saved + growthObstruction(helper, core, ship));
                for (var part : DinghyGrowth.body()) helper.assertTrue(BioshipIntegration.isIntegratedMachine(level, core.getBlockPos().offset(part.offset())) || level.getBlockState(core.getBlockPos().offset(part.offset())).is(part.state().getBlock()), "Missing part " + part.offset());
                helper.assertTrue(core.swimmingFlagella() == 6, "Body did not grow six functional flagella");
                helper.assertTrue(level.getBlockState(core.getBlockPos().offset(0, 5, 0)).is(Create_bioships.FLESH.get()), "Cabin has no roof");
                helper.assertTrue(level.getBlockState(core.getBlockPos().offset(1, 2, 1)).isAir(), "Strafe control still clutters the aisle");
                var doorPos = core.getBlockPos().offset(DinghyGrowth.DOOR);
                Create_bioships.DOOR.get().setOpen(null, level, level.getBlockState(doorPos), doorPos, true);
                helper.assertTrue(level.getBlockState(doorPos.above()).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Upper valve half did not open");
                Create_bioships.DOOR.get().setOpen(null, level, level.getBlockState(doorPos), doorPos, false);
                helper.assertTrue(!level.getBlockState(doorPos.above()).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "Upper valve half did not close");
                var helm = (SteeringWheelBlockEntity) level.getBlockEntity(core.getBlockPos().offset(DinghyGrowth.HELM));
                helper.assertTrue(helm != null && helm.getBlockState().getBlock() instanceof SteeringWheelBlock, "Helm does not use the native interaction/overlay");
                helper.assertTrue(helm.material.is(Blocks.CRIMSON_PLANKS), "Helm lost organic material");
                var restored = BlockEntity.loadStatic(core.getBlockPos(), core.getBlockState(), saved, level.registryAccess());
                helper.assertTrue(restored instanceof LivingCoreBlockEntity && restored.saveWithFullMetadata(level.registryAccess()).getInt("Stage") == 2, "Growth did not persist");
                helper.assertTrue(!core.canFly(), "Biomass-only body enabled flight");
                enableFlight(helper, core);
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
                        helper.assertTrue(core.canFly(), "Mature amalgam organs did not enable flight");
                        for (BlockPos organ : DinghyGrowth.BUOYANCY_ORGANS) helper.assertTrue(level.getBlockState(core.getBlockPos().offset(organ)).is(Create_bioships.BUOYANCY_ORGAN.get()), "Amalgam did not become visible living tissue");
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
    @GameTest(template = "test_empty", timeoutTicks = 1200, batch = "bioship_hover")
    public static void restingShipHoldsHeightAndCabinLightsPulse(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(800, () -> {
            try {
                var core = core(ship); var level = helper.getLevel();
                enableFlight(helper, core);
                core.interact(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), ItemStack.EMPTY);
                double height = ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(core.getBlockPos())).y;
                BlockPos lamp = core.getBlockPos().offset(-2, 4, -2);
                boolean first = level.getBlockState(lamp).getValue(PulseLightBlock.LIT);
                final boolean[] changed = {false};
                final boolean[] eyeStates = {false, false};
                int untilBlink = Math.floorMod(-level.getGameTime() - core.getBlockPos().getZ(), 120);
                helper.runAfterDelay(untilBlink + 1, () -> eyeStates[1] = level.getBlockState(core.getBlockPos()).getValue(LivingCoreBlock.BLINK));
                helper.runAfterDelay(untilBlink + 30, () -> eyeStates[0] = !level.getBlockState(core.getBlockPos()).getValue(LivingCoreBlock.BLINK));
                for (int delay = 6; delay <= 30; delay += 6) helper.runAfterDelay(delay, () -> {
                    if (level.getBlockState(lamp).getValue(PulseLightBlock.LIT) != first) changed[0] = true;
                });
                helper.runAfterDelay(160, () -> {
                    try {
                        double after = ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(core.getBlockPos())).y;
                        helper.assertTrue(Math.abs(after - height) < 0.5, "Resting ship drifted away vertically: start=" + height + ", end=" + after + ", velocity=" + ship.latestLinearVelocity);
                        helper.assertTrue(changed[0], "Bioluminescent cabin lights did not pulse");
                        helper.assertTrue(eyeStates[0] && eyeStates[1], "Active eye did not open and blink"); helper.succeed();
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
    @GameTest(template = "test_empty", timeoutTicks = 1200, batch = "bioship_mouth")
    public static void developedMouthConsumesCapturedPrey(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try { feed(helper, core(ship)); } catch (Throwable failure) { cleanup(helper, ship); throw failure; }
        helper.runAtTickTime(800, () -> {
            try {
                var core = core(ship); var level = helper.getLevel();
                helper.assertTrue(core.hasMouth(), "Integrated Maw Hopper did not provide a mouth");
                enableFlight(helper, core);
                var hunt = core.getBlockPos().offset(DinghyGrowth.HUNT);
                level.setBlock(hunt, level.getBlockState(hunt).setValue(NerveControlBlock.LATCHED, true).setValue(NerveControlBlock.ACTIVE, true), 3);
                helper.runAfterDelay(80, () -> {
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
    @GameTest(template = "test_empty", timeoutTicks = 100, batch = "bioship_integration")
    public static void placementAssimilatesMachinesAndTissueWithoutFeeding(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos pos = fixturePosition();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        for (BlockPos ground : BlockPos.betweenClosed(pos.offset(-5, -1, -5), pos.offset(5, -1, 5))) level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
        BlockPos machine = pos.east();
        level.setBlock(machine, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("biomancy", "maw_hopper")).defaultBlockState(), 3);
        BlockPos storage = pos.south();
        level.setBlock(storage, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("biomancy", "storage_sac")).defaultBlockState(), 3);
        var inventory = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, storage, null);
        helper.assertTrue(inventory != null, "Maw has no inventory capability");
        var flesh = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("biomancy", "flesh_bits"));
        helper.assertTrue(inventory.insertItem(0, new ItemStack(flesh, 10), false).isEmpty(), "Could not seed tissue inventory");
        inventory.insertItem(1, new ItemStack(Items.DIAMOND, 3), false);
        level.setBlock(pos.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("biomancy", "flesh")).defaultBlockState(), 3);
        level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
        var initial = (LivingCoreBlockEntity)level.getBlockEntity(pos);
        var food = new ItemStack(Items.BEEF, 2);
        helper.assertTrue(!initial.feedAtMouth(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), food), "Unassembled eye ate food");
        Create_bioships.CORE.get().setPlacedBy(level, pos, initial.getBlockState(), playerForPlacement(helper), Create_bioships.CORE_ITEM.get().getDefaultInstance());
        for (int tick = 2; tick <= 30; tick += 2) helper.runAtTickTime(tick, () -> {
            for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) if (candidate instanceof ServerSubLevel server && BioshipIntegration.bound(server))
                SubLevelContainer.getContainer(level).addForceLoadTicket(server, dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
        });
        helper.runAtTickTime(40, () -> {
            ServerSubLevel ship = null;
            for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) if (candidate instanceof ServerSubLevel server) {
                boolean hasCore = false;
                for (var actor : server.getPlot().getBlockEntityActors()) if (actor instanceof LivingCoreBlockEntity) hasCore = true;
                var physical = server.logicalPose().position();
                if (hasCore && Math.abs(physical.x() - pos.getX()) < 10 && Math.abs(physical.z() - pos.getZ()) < 10) { ship = server; break; }
            }
            helper.assertTrue(ship != null, "Placement did not automatically create Sable object");
            try {
                var core = core(ship); var data = core.saveWithFullMetadata(level.registryAccess());
                helper.assertTrue(core.hasMouth(), "Nearby Maw Hopper was not assimilated: " + describeShips(helper) + " source=" + level.getBlockState(machine));
                helper.assertTrue(data.getInt("Biomass") == 11, "Stored and neighboring tissue not credited exactly once: " + data);
                helper.assertTrue(data.getInt("Satiety") <= 600 && data.getInt("Trust") == 60, "Assimilation fed or rewarded the creature");
                BlockPos stored = null;
                for (var block : BioshipIntegration.blockEntities(ship)) if (BuiltInRegistries.BLOCK.getKey(block.getBlockState().getBlock()).getPath().equals("storage_sac")) stored = block.getBlockPos();
                helper.assertTrue(stored != null, "Storage sac was not assimilated");
                var moved = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, stored, null);
                helper.assertTrue(moved != null && moved.getStackInSlot(1).is(Items.DIAMOND) && moved.getStackInSlot(1).getCount() == 3, "Assimilation lost unrelated inventory");
                helper.assertTrue(level.getBlockState(machine).isAir() && level.getBlockState(pos.west()).isAir(), "Assimilation duplicated source blocks");
                helper.assertTrue(!core.canFly() && ship.logicalPose().transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(core.getBlockPos())).y < 81, "Unfed hatchling flew upward");
                boolean rejected = false;
                try { dev.simulated_team.simulated.content.blocks.physics_assembler.assembly_preventer.DisassemblyPrevention.checkSubLevelForPrimary(level, core.mouthBlock()); }
                catch (com.simibubi.create.content.contraptions.AssemblyException expected) { rejected = true; }
                helper.assertTrue(rejected, "Assimilated machine allowed disassembly");
                dev.simulated_team.simulated.util.SimAssemblyHelper.disassembleSubLevel(level, ship, core.getBlockPos(), pos, net.minecraft.world.level.block.Rotation.NONE, false);
                helper.assertTrue(level.getBlockState(core.getBlockPos()).is(Create_bioships.CORE.get()), "Direct disassembly removed living core");
                helper.succeed();
            } finally { cleanup(helper, ship); }
        });
    }
    @GameTest(template = "test_empty", batch = "bioship_diet")
    public static void skeletonsAreInedibleAndZombiesPoison(GameTestHelper helper) {
        var skeleton = net.minecraft.world.entity.EntityType.SKELETON.create(helper.getLevel());
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(!TentacleBlockEntity.edible(skeleton), "Skeleton counted as food");
        helper.assertTrue(TentacleBlockEntity.edible(zombie), "Zombie poisoning path is unreachable");
        helper.assertTrue(TentacleBlockEntity.edible(villager), "Hunting cannot threaten villagers");
        helper.succeed();
    }
    @GameTest(template = "test_empty", batch = "bioship_temperament")
    public static void huntingPermissionAndNegativeTrustChangeTargets(GameTestHelper helper) {
        ServerSubLevel ship = assemble(helper);
        try {
            var core = core(ship); var level = helper.getLevel(); var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var food = new ItemStack(Items.BEEF, 2);
            helper.assertTrue(!core.feedAtMouth(player, food) && food.getCount() == 2, "Assembled eye ate without a mouth");
            core.eatPrey(); helper.assertTrue(core.saveWithFullMetadata(level.registryAccess()).getInt("Satiety") == 600, "Mouthless ship ate prey");
            level.setBlock(core.getBlockPos().north(), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("biomancy", "maw_hopper")).defaultBlockState(), 3);
            helper.assertTrue(core.feedAtMouth(player, food), "Maw did not enable feeding");
            var saved = core.saveWithFullMetadata(level.registryAccess()); saved.putInt("Stage", 2); saved.putInt("Satiety", 100); saved.putInt("Trust", 60);
            core.loadAdditional(saved, level.registryAccess());
            var limb = new TentacleBlockEntity(core.getBlockPos(), Create_bioships.TENTACLE.get().defaultBlockState());
            var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            helper.assertTrue(!limb.valid(villager, core), "Cooperative ship hunted with permission off");
            var hunt = Create_bioships.CONTROLS.stream().filter(c -> c.get().command == NerveControlBlock.Command.HUNT).findFirst().orElseThrow().get();
            level.setBlock(core.getBlockPos().offset(DinghyGrowth.HUNT), hunt.defaultBlockState().setValue(NerveControlBlock.LATCHED, true).setValue(NerveControlBlock.ACTIVE, true), 3);
            helper.assertTrue(limb.valid(villager, core), "Permitted hungry ship ignored villager prey");
            helper.assertTrue(!limb.valid(player, core), "Cooperative ship hunted its captain");
            saved.putInt("Trust", -1); saved.putBoolean("Resting", true); saved.putInt("PoisonSeconds", 12); core.loadAdditional(saved, level.registryAccess());
            helper.assertTrue(core.aggressive() && !core.resting() && limb.valid(player, core), "Negative trust did not trigger aggression");
            helper.assertTrue(core.saveWithFullMetadata(level.registryAccess()).getInt("PoisonSeconds") == 12, "Poison did not persist");
            helper.succeed();
        } finally { cleanup(helper, ship); }
    }
}
