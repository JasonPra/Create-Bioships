package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.compat.letitwalk.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.legs.*;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class LimbSettingsTests {
  @GameTest(template = "test_empty", timeoutTicks = 40, batch = "limb_settings_cleanup")
  public static void removal_clears_only_that_components_tuning(GameTestHelper h) {
    var level = h.getLevel();
    var at = h.absolutePos(new BlockPos(1, 4, 1));
    level.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(at.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.east(2),
        ComponentRegistry.CRAB_LEG
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
        Block.UPDATE_ALL);
    level.setBlock(
        at.south(), ComponentRegistry.LIMB_JOINT.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            at,
            List.of(at, at.east(), at.east(2), at.south()),
            new BoundingBox3i(at, at.offset(2, 0, 1)));
    var socket = body.getPlot().getCenterBlock().east(2);
    var jointCell = body.getPlot().getCenterBlock().south();
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(body);
    var tuned = new LimbTuning(20, 1.75, 1.5);
    tuned.save(level, socket);
    new LimbTuning(0, 1.25, .5).save(level, jointCell);
    level.setBlock(
        socket, level.getBlockState(socket).setValue(CrabLegBlock.HEIGHT, 3), Block.UPDATE_ALL);
    h.assertTrue(
        LimbTuning.get(level, socket).equals(tuned), "Changing height cleared live tuning");
    level.removeBlock(socket, false);
    h.assertTrue(
        !body.getUserDataTag()
            .getCompound(LimbTuning.DATA)
            .contains(Long.toString(socket.asLong())),
        "Removed socket retained saved tuning");
    h.assertTrue(
        LimbTuning.get(level, jointCell).stiffness() == 1.25,
        "Removing one leg cleared another leg's settings");
    level.setBlock(
        socket,
        ComponentRegistry.CRAB_LEG
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
        Block.UPDATE_ALL);
    h.assertTrue(
        LimbTuning.get(level, socket).equals(LimbTuning.DEFAULT)
            && level.getBlockState(socket).getValue(CrabLegBlock.HEIGHT) == 1,
        "Replacement compact leg inherited old settings");
    ((ModularJointEntity) level.getBlockEntity(jointCell)).setRestOffset(55);
    level.removeBlock(jointCell, false);
    h.assertTrue(
        !body.getUserDataTag().contains(LimbTuning.DATA), "Empty tuning registry was retained");
    level.setBlock(
        jointCell, ComponentRegistry.LIMB_JOINT.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        ((ModularJointEntity) level.getBlockEntity(jointCell)).restOffset() == 0
            && LimbTuning.get(level, jointCell).equals(LimbTuning.DEFAULT),
        "Replacement modular joint inherited settings");
    h.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 40, batch = "limb_settings_reset")
  public static void positioner_reset_restores_defaults_and_respects_tool_validation(
      GameTestHelper h) {
    var level = h.getLevel();
    var at = h.absolutePos(new BlockPos(1, 4, 1));
    level.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(at.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.east(2),
        ComponentRegistry.CRAB_LEG
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(CrabLegBlock.HEIGHT, 3),
        Block.UPDATE_ALL);
    level.setBlock(
        at.south(), ComponentRegistry.LIMB_SWIVEL.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            at,
            List.of(at, at.east(), at.east(2), at.south()),
            new BoundingBox3i(at, at.offset(2, 0, 1)));
    var socket = body.getPlot().getCenterBlock().east(2);
    var joint = body.getPlot().getCenterBlock().south();
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(body);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LimbReset"));
    player.setPos(body.logicalPose().transformPosition(Vec3.atCenterOf(socket)));
    player.setItemInHand(
        InteractionHand.MAIN_HAND, Create_bioships.LIMB_POSITIONER.get().getDefaultInstance());
    new LimbTuning(30, 1.75, .5).save(level, socket);
    new LimbTuning(0, .5, 1.75).save(level, joint);
    ((ModularJointEntity) level.getBlockEntity(joint)).setRestOffset(65);
    h.assertTrue(
        LimbPositionerActions.configure(player, socket, LimbPositionerActions.RESET, 0),
        "Compact reset was rejected");
    h.assertTrue(
        level.getBlockState(socket).getValue(CrabLegBlock.HEIGHT) == 1
            && LimbTuning.get(level, socket).equals(LimbTuning.DEFAULT),
        "Compact reset did not restore all defaults");
    h.assertTrue(
        LimbTuning.get(level, joint).damping() == 1.75, "Reset changed a different component");
    h.assertTrue(
        LimbPositionerActions.configure(player, joint, LimbPositionerActions.RESET, 0),
        "Modular reset was rejected");
    h.assertTrue(
        ((ModularJointEntity) level.getBlockEntity(joint)).restOffset() == 0
            && LimbTuning.get(level, joint).equals(LimbTuning.DEFAULT)
            && !body.getUserDataTag().contains(LimbTuning.DATA),
        "Modular reset retained custom settings");
    new LimbTuning(15, 1.5, 1.5).save(level, socket);
    player.setPos(player.position().add(50, 0, 0));
    h.assertTrue(
        !LimbPositionerActions.configure(player, socket, LimbPositionerActions.RESET, 0)
            && LimbTuning.get(level, socket).restAngle() == 15,
        "Distant reset bypassed reach validation");
    player.setPos(body.logicalPose().transformPosition(Vec3.atCenterOf(socket)));
    player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
    h.assertTrue(
        !LimbPositionerActions.configure(player, socket, LimbPositionerActions.RESET, 0),
        "Reset worked without our Positioner");
    h.succeed();
  }
}
