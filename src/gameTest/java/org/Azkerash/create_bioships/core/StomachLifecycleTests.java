package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class StomachLifecycleTests {
  @GameTest(template = "test_empty", batch = "stomach_lifecycle")
  public static void only_flagged_alive_grids_process_and_inactive_states_preserve_inventory(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    var state = ComponentRegistry.STOMACH.get().defaultBlockState();
    level.setBlock(pos, state, 3);
    var stomach = (StomachBlockEntity) level.getBlockEntity(pos);
    stomach.enqueue(new ItemStack(Items.PORKCHOP, 3), false);
    stomach.setItem(9, new ItemStack(StomachBlockEntity.paste(), 5));
    frozen(helper, stomach, "World stomach ran");
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, java.util.List.of(pos), new BoundingBox3i(pos, pos));
    stomach = (StomachBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    frozen(helper, stomach, "Unflagged grid stomach ran");
    SubLevelData.living(body, true);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    for (int i = 0; i < 25; i++) stomach.advance();
    helper.assertTrue(
        stomach.progress() > 0 && stomach.acid() < 200, "Living mouthless grid failed to digest");
    SubLevelData.living(body, false);
    frozen(helper, stomach, "Disabled living flag did not pause processing");
    SubLevelData.living(body, true);
    int progress = stomach.progress();
    stomach.advance();
    helper.assertTrue(
        stomach.progress() == progress + 1, "Living grid failed to resume saved progress");
    SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test");
    frozen(helper, stomach, "Dead stomach ran or discarded contents");
    helper.runAfterDelay(10, helper::succeed);
  }

  private static void frozen(GameTestHelper helper, StomachBlockEntity stomach, String message) {
    var before = stomach.saveWithFullMetadata(helper.getLevel().registryAccess());
    for (int i = 0; i < 100; i++) stomach.advance();
    helper.assertTrue(
        before.equals(stomach.saveWithFullMetadata(helper.getLevel().registryAccess())), message);
  }

  @GameTest(template = "test_empty", batch = "stomach_wrench_storage", timeoutTicks = 160)
  public static void native_wrench_drop_and_replacement_keep_stomach_inventory_and_progress(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(1408, 80, 128);
    for (int x = 87; x <= 88; x++) for (int z = 7; z <= 8; z++) level.setChunkForced(x, z, true);
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(origin), "Waiting for item-drop chunks"))
        .thenExecute(
            () -> {
              for (int x = -3; x <= 5; x++)
                for (int z = -3; z <= 5; z++)
                  level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
              level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
              level.setBlock(origin.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
              var body =
                  SubLevelAssemblyHelper.assembleBlocks(
                      level,
                      origin,
                      java.util.List.of(origin, origin.east()),
                      new BoundingBox3i(origin, origin.east()));
              var core = body.getPlot().getCenterBlock();
              CoreAssemblyRules.mark(level, core);
              SubLevelData.hungerPaused(body, true);
              SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
              var stomach = (StomachBlockEntity) level.getBlockEntity(core.east());
              stomach.enqueue(new ItemStack(Items.PORKCHOP, 3), false);
              stomach.setItem(9, new ItemStack(StomachBlockEntity.paste(), 5));
              for (int i = 0; i < 25; i++) stomach.advance();
              int acid = stomach.acid(), progress = stomach.progress();
              var player =
                  new net.neoforged.neoforge.common.util.FakePlayer(
                      level,
                      new com.mojang.authlib.GameProfile(
                          java.util.UUID.randomUUID(), "StomachWrench"));
              player.setPos(
                  body.logicalPose().transformPosition(Vec3.atCenterOf(core).add(0, 0, -2)));
              player.setShiftKeyDown(true);
              player.setItemInHand(
                  InteractionHand.MAIN_HAND,
                  Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
              var point = body.logicalPose().transformPosition(Vec3.atCenterOf(core.east()));
              player.gameMode.useItemOn(
                  player,
                  level,
                  player.getMainHandItem(),
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(
                      Vec3.atCenterOf(core.east()), Direction.NORTH, core.east(), false));
              var drops =
                  level.getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new AABB(point, point).inflate(1),
                      e -> e.getItem().is(ComponentRegistry.STOMACH_ITEM.get()));
              helper.assertTrue(
                  level.getBlockState(core.east()).isAir() && drops.size() == 1,
                  "Wrench failed to drop one stomach");
              var drop = drops.getFirst();
              var carried = drop.getItem().copy();
              helper.assertTrue(
                  carried.has(DataComponents.BLOCK_ENTITY_DATA), "Wrench drop lost stomach data");
              drop.discard();
              var placed = origin.south(4);
              player.setShiftKeyDown(false);
              player.setPos(Vec3.atCenterOf(placed).add(0, 0, -2));
              player.setItemInHand(InteractionHand.MAIN_HAND, carried);
              var result =
                  ((BlockItem) carried.getItem())
                      .place(
                          new BlockPlaceContext(
                              new UseOnContext(
                                  player,
                                  InteractionHand.MAIN_HAND,
                                  new BlockHitResult(
                                      Vec3.atCenterOf(placed.below()).add(0, .5, 0),
                                      Direction.UP,
                                      placed.below(),
                                      false))));
              helper.assertTrue(
                  result.consumesAction()
                      && level.getBlockEntity(placed) instanceof StomachBlockEntity,
                  "Native placement failed");
              var restored = (StomachBlockEntity) level.getBlockEntity(placed);
              helper.assertTrue(
                  restored.queued() == 3
                      && restored.getItem(9).getCount() == 5
                      && restored.acid() == acid
                      && restored.progress() == progress,
                  "Wrench/placement lost inventory or digestion progress");
              frozen(helper, restored, "Replaced world stomach continued processing");
            })
        .thenExecuteAfter(10, helper::succeed);
  }
}
