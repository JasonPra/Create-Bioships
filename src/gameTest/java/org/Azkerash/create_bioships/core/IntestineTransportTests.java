package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.MouthBlockEntity;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;
import org.Azkerash.create_bioships.core.digestion.SubLevelDigestion;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class IntestineTransportTests {
  @GameTest(template = "test_empty", batch = "intestine_delivery", timeoutTicks = 160)
  public static void mouth_routes_food_without_loss_and_broken_pipes_stop_delivery(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(pos, ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        pos.east(), ComponentRegistry.INTESTINE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        pos.east(2), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    var grid =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, List.of(pos, pos.east(), pos.east(2)), new BoundingBox3i(pos, pos.east(2)));
    SubLevelData.living(grid, true);
    DevelopmentLifeSupport.pause(grid);
    pos = grid.getPlot().getCenterBlock();
    var mouth = (MouthBlockEntity) level.getBlockEntity(pos);
    var stomach = (StomachBlockEntity) level.getBlockEntity(pos.east(2));
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FeedMouth"));
    player.getAbilities().instabuild = false;
    var food = new ItemStack(Items.PORKCHOP, 4);
    player.setItemInHand(InteractionHand.MAIN_HAND, food);
    level
        .getBlockState(pos)
        .useItemOn(
            food,
            level,
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    helper.assertTrue(
        food.getCount() == 3 && mouth.getItem(0).getCount() == 1,
        "Hand feeding did not move exactly one item");
    helper.assertTrue(
        mouth.feed(new ItemStack(Items.STONE), false).getCount() == 1,
        "Mouth accepted non-digestible stone");
    helper.assertTrue(
        SubLevelDigestion.transfer(mouth) == 1 && mouth.isEmpty() && stomach.queued() == 1,
        "Food did not follow face-connected intestine route");
    mouth.feed(new ItemStack(Items.PORKCHOP, 2), false);
    level.removeBlock(pos.east(), false);
    helper.assertTrue(
        SubLevelDigestion.transfer(mouth) == 0 && mouth.getItem(0).getCount() == 2,
        "Cached route crossed broken intestine");
    level.setBlock(
        pos.east(), ComponentRegistry.INTESTINE.get().defaultBlockState(), Block.UPDATE_ALL);
    for (int i = 0; i < 9; i++) {
      stomach.setItem(i, new ItemStack(Items.PORKCHOP, 64));
    }
    helper.assertTrue(
        SubLevelDigestion.transfer(mouth) == 0 && mouth.getItem(0).getCount() == 2,
        "Full stomach consumed intake items");
    var drop =
        net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, mouth)
            .getFirst();
    helper.assertTrue(
        drop.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),
        "Removed mouth lost its inventory data");
    var menu = mouth.createMenu(1, player.getInventory(), player);
    helper.assertTrue(
        menu.getSlot(0).mayPlace(new ItemStack(Items.PORKCHOP))
            && !menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)),
        "Mouth GUI has wrong intake filter");
    player.setItemInHand(
        InteractionHand.MAIN_HAND, Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
    player.setPos(Vec3.atCenterOf(pos).add(0, 0, -2));
    for (var organ : List.of(pos, pos.east(2))) {
      var before =
          level
              .getBlockState(organ)
              .getValue(
                  net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
      player.gameMode.useItemOn(
          player,
          level,
          player.getMainHandItem(),
          InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(organ), Direction.UP, organ, false));
      helper.assertTrue(
          level
                  .getBlockState(organ)
                  .getValue(
                      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
              != before,
          "Organ UI intercepted normal wrench rotation");
    }
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "stomach_nutrition", timeoutTicks = 160)
  public static void stored_paste_feeds_only_a_living_hungry_creature_after_eating_time(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = new BlockPos(1216, 80, 128);
    level.setChunkForced(76, 8, true);
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(pos, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              pos.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              pos.west(), ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  pos,
                  List.of(pos, pos.east(), pos.west()),
                  new BoundingBox3i(pos.west(), pos.east()));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          DevelopmentLifeSupport.pause(body);
          SubLevelData.hunger(body, 1);
          var stomach = (StomachBlockEntity) level.getBlockEntity(core.east());
          stomach.setItem(9, new ItemStack(StomachBlockEntity.paste(), 3));
          for (int i = 0; i < 31; i++) {
            stomach.advance();
          }
          helper.assertTrue(
              SubLevelData.hunger(body) == 1, "Paste consumed faster than player eating");
          stomach.advance();
          helper.assertTrue(
              SubLevelData.hunger(body) == 5 && stomach.getItem(9).getCount() == 2,
              "One paste did not restore four hunger");
          SubLevelData.hunger(body, SubLevelData.maximumHunger(body) - 1);
          stomach.setItem(9, new ItemStack(StomachBlockEntity.paste(), 6));
          for (int i = 0; i < 64; i++) {
            stomach.advance();
          }
          helper.assertTrue(
              SubLevelData.hunger(body) == 10 && stomach.getItem(9).getCount() == 5,
              "Paste did not respect hunger cap");
          stomach.setItem(9, new ItemStack(StomachBlockEntity.paste(), 2));
          for (int i = 0; i < 64; i++) {
            stomach.advance();
          }
          helper.assertTrue(stomach.getItem(9).getCount() == 2, "Full creature wasted paste");
          SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test");
          int acid = stomach.acid();
          for (int i = 0; i < 64; i++) {
            stomach.advance();
          }
          helper.assertTrue(
              stomach.acid() == acid && stomach.getItem(9).getCount() == 2,
              "Dead stomach remained active");
          helper.runAfterDelay(10, helper::succeed);
        });
  }
}
