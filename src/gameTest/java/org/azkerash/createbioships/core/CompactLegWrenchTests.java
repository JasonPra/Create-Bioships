package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.compat.letitwalk.CrabLegKinematics;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CrabLegBlock;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class CompactLegWrenchTests {
  @GameTest(template = "test_empty", timeoutTicks = 80, batch = "compact_leg_wrench")
  public static void ossified_wrench_extends_thickens_caps_and_removes_without_rotation(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var at = helper.absolutePos(new BlockPos(1, 3, 1));
    level.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(at.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.east(2),
        ComponentRegistry.CRAB_LEG
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
        Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, at, List.of(at, at.east(), at.east(2)), new BoundingBox3i(at, at.east(2)));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    var pos = core.east(2);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LegWrench"));
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
    var standard =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse("create:wrench"));
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(standard));
    player.gameMode.useItemOn(
        player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(
        level.getBlockState(pos).getValue(CrabLegBlock.HEIGHT) == 1,
        "Create wrench extended a compact leg");
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(CreateBioships.BIOSHIP_WRENCH.get()));
    for (int expected : new int[] {2, 3, 3}) {
      player.gameMode.useItemOn(
          player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
      var state = level.getBlockState(pos);
      helper.assertTrue(
          state.getValue(CrabLegBlock.HEIGHT) == expected, "Extension height/cap incorrect");
      helper.assertTrue(
          state.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
          "Wrench rotated leg instead of extending it");
    }
    helper.assertTrue(
        new CrabLegKinematics(3).standingHeight() == 3.5
            && CrabLegKinematics.thickness(3) > CrabLegKinematics.thickness(1),
        "Extended legs did not get taller and thicker");
    helper.assertTrue(
        SubLevelArmor.status(body).maximum() == 10, "Extension changed armor capacity");
    org.azkerash.createbioships.core.access.SubLevelPermissions.assignIfUnowned(
        body, player.getUUID());
    player.setShiftKeyDown(true);
    player.gameMode.useItemOn(
        player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(level.getBlockState(pos).isAir(), "Shift-wrench did not remove leg");
    helper.succeed();
  }
}
