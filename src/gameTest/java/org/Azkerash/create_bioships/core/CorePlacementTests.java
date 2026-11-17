package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CorePlacementTests {
  @GameTest(template = "test_empty", timeoutTicks = 120, batch = "player_core_assembly")
  public static void only_direct_player_placement_automatically_assembles(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(2, 3, 2));
    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
    var generated = pos.east(8);
    level.setBlock(generated, Create_bioships.CORE.get().defaultBlockState(), 3);
    var player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(pos.getX() + 2, pos.getY() + 1, pos.getZ() + 2);
    var stack = new ItemStack(Create_bioships.CORE_ITEM.get(), 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    var hit =
        new BlockHitResult(
            Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false);
    var result =
        Create_bioships.CORE_ITEM
            .get()
            .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    helper.assertTrue(result.consumesAction(), "Native player placement failed");
    helper.assertTrue(
        level.getBlockState(pos).is(Create_bioships.CORE.get()),
        "Player item did not place a core at its target: " + level.getBlockState(pos));
    helper.runAfterDelay(
        2,
        () -> {
          for (var body : SubLevelContainer.getContainer(level).getAllSubLevels())
            if (!body.isRemoved()
                && CoreAssemblyRules.isCreature(body)
                && body.logicalPose()
                        .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()))
                        .distanceToSqr(Vec3.atCenterOf(pos))
                    < 4)
              SubLevelContainer.getContainer(level)
                  .addForceLoadTicket(
                      body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        });
    helper.runAfterDelay(
        5,
        () -> {
          helper.assertTrue(
              level.getBlockState(pos).isAir(), "Player-placed core stayed unassembled");
          var bodies =
              SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                  .filter(
                      b ->
                          !b.isRemoved()
                              && CoreAssemblyRules.isCreature(b)
                              && b.logicalPose()
                                      .transformPosition(
                                          Vec3.atCenterOf(b.getPlot().getCenterBlock()))
                                      .distanceToSqr(Vec3.atCenterOf(pos))
                                  < 4)
                  .toList();
          helper.assertTrue(
              bodies.size() == 1,
              "Player placement did not create exactly one Sable object; count="
                  + bodies.size()
                  + " all="
                  + SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                      .map(
                          b ->
                              b.getUniqueId()
                                  + ":"
                                  + b.logicalPose()
                                      .transformPosition(
                                          Vec3.atCenterOf(b.getPlot().getCenterBlock()))
                                  + ":"
                                  + CoreAssemblyRules.isCreature(b))
                      .toList());
          helper.assertTrue(
              level.getBlockState(generated).is(Create_bioships.CORE.get())
                  && level.getBlockState(generated).getValue(CoreBlock.BLINK),
              "Generated core auto-assembled or opened");
          helper.assertTrue(
              stack.getCount() == 1, "Auto-assembly duplicated or consumed extra core items");
          SubLevelContainer.getContainer(level)
              .removeSubLevel(bodies.getFirst(), SubLevelRemovalReason.REMOVED);
          level.removeBlock(generated, false);
          helper.succeed();
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "core_whitelist")
  public static void every_biomancy_id_is_allowed_and_forbidden_clicks_stop_before_placement(
      GameTestHelper helper) {
    int biomancy = 0;
    for (var id : BuiltInRegistries.BLOCK.keySet())
      if (id.getNamespace().equals("biomancy")) {
        biomancy++;
        helper.assertTrue(
            BlockPlacementRules.allowedIds().contains(id)
                && BlockPlacementRules.allows(BuiltInRegistries.BLOCK.get(id).defaultBlockState()),
            "Missing Biomancy block: " + id);
      }
    helper.assertTrue(biomancy > 0, "Biomancy registry was empty");
    System.out.println("CORE_WHITELIST verifiedBiomancyIds=" + biomancy);
    var level = helper.getLevel();
    var pos = new BlockPos(416, 80, 32);
    level.getChunk(26, 2);
    level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, java.util.List.of(pos), new BoundingBox3i(pos, pos));
    var logical = body.getPlot().getCenterBlock();
    var player = helper.makeMockPlayer(GameType.CREATIVE);
    var hit = new BlockHitResult(Vec3.atCenterOf(logical), Direction.UP, logical, false);
    for (var item :
        java.util.List.of(
            Items.STONE,
            BuiltInRegistries.ITEM.get(ResourceLocation.parse("simulated:physics_assembler")))) {
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
      var click =
          new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, logical, hit);
      NeoForge.EVENT_BUS.post(click);
      helper.assertTrue(click.isCanceled(), "Forbidden block reached placement: " + item);
    }
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Create_bioships.CORE_ITEM.get()));
    var allowed =
        new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, logical, hit);
    NeoForge.EVENT_BUS.post(allowed);
    helper.assertTrue(!allowed.isCanceled(), "Own core was not whitelisted");
    helper.assertTrue(
        level.getBlockState(logical.above()).isAir(), "Denied click changed the creature grid");
    SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    helper.succeed();
  }
}
