package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class WrenchRemovalTests {
  @GameTest(template = "test_empty", timeoutTicks = 120, batch = "safe_component_removal")
  public static void create_pickup_preserves_life_and_dead_core_drops_nothing(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(368, 80, 128);
    level.setBlock(origin, Create_bioships.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), Create_bioships.LAND_EYE.get().defaultBlockState(), 3);
    level.setBlock(origin.west(), Blocks.CHEST.defaultBlockState(), 3);
    var flesh =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "biomancy", "packed_flesh"));
    level.setBlock(origin.north(), flesh.defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east(), origin.west(), origin.north()),
            new BoundingBox3i(origin.west().north(), origin.east()));
    helper.assertTrue(body != null, "Wrench fixture assembly failed");
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelHealth.damage(body, 35, "test");
    var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wrench-test");
    var player =
        new net.minecraft.server.level.ServerPlayer(
            level.getServer(),
            level,
            profile,
            net.minecraft.server.level.ClientInformation.createDefault());
    player.connection =
        new net.neoforged.neoforge.common.util.FakePlayer(level, profile).connection;
    player.setPos(body.logicalPose().transformPosition(Vec3.atCenterOf(core).add(0, 0, -2)));
    player.setShiftKeyDown(true);
    player.setItemInHand(
        InteractionHand.MAIN_HAND, Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
    var wrench = Create_bioships.BIOSHIP_WRENCH.get();
    player.setShiftKeyDown(false);
    player.gameMode.useItemOn(
        player,
        level,
        player.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(core.east()), Direction.NORTH, core.east(), false));
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 5, "Normal wrench right-click healed creature");
    player.setShiftKeyDown(true);
    // A failed/rolled-back removal must not convert the restored block into free health.
    var eyeState = level.getBlockState(core.east());
    SubLevelHealth.withoutRemovalDamage(
        body,
        () -> {
          level.removeBlock(core.east(), false);
          level.setBlock(core.east(), eyeState, 3);
          return net.minecraft.world.InteractionResult.PASS;
        });
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 5 && SubLevelHealth.status(body).maximum() == 40,
        "Wrench rollback healed the creature");
    for (var pos : List.of(core.east(), core.west(), core.north())) {
      var result =
          player.gameMode.useItemOn(
              player,
              level,
              player.getMainHandItem(),
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
      helper.assertTrue(
          result.consumesAction(), "Normal player Shift-wrench pickup was not handled");
      helper.assertTrue(
          level.getBlockState(pos).isAir(), "Wrench failed to remove non-core component");
      helper.assertTrue(
          SubLevelHealth.status(body).current() == 5 && !SubLevelHealth.status(body).dead(),
          "Wrench removal dealt damage or killed the creature");
    }
    helper.assertTrue(
        SubLevelHealth.status(body).maximum() == 10, "Wrench removal did not update capacity");
    helper.assertTrue(
        player.getInventory().countItem(Create_bioships.LAND_EYE.get().asItem()) == 1,
        "Create pickup lost eye item");
    helper.assertTrue(
        player.getInventory().countItem(Items.CHEST) == 1, "Create pickup lost chest item");
    helper.assertTrue(
        player.getInventory().countItem(flesh.asItem()) == 1,
        "Create pickup lost packed flesh block");
    var coreContext =
        new UseOnContext(
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(core), Direction.NORTH, core, false));
    wrench.useOn(coreContext);
    helper.assertTrue(
        level.getBlockState(core).is(Create_bioships.CORE.get()), "Wrench picked up living core");
    SubLevelHealth.damage(body, 5, "test");
    wrench.useOn(coreContext);
    helper.assertTrue(
        level.getBlockState(core).is(Create_bioships.CORE.get()), "Wrench picked up dead core");
    level.setBlock(core.east(), eyeState, 3);
    player.gameMode.useItemOn(
        player,
        level,
        player.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(core.east()), Direction.NORTH, core.east(), false));
    helper.assertTrue(
        level.getBlockState(core.east()).isAir(),
        "Normal player could not wrench a dead creature component");
    helper.assertTrue(
        SubLevelHealth.status(body).current() == 0 && SubLevelHealth.status(body).dead(),
        "Wrench revived or healed dead creature");
    var outside = origin.east(4);
    level.setBlock(outside, com.simibubi.create.AllBlocks.SHAFT.get().defaultBlockState(), 3);
    player.gameMode.useItemOn(
        player,
        level,
        player.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(outside), Direction.NORTH, outside, false));
    helper.assertTrue(
        level.getBlockState(outside).isAir(),
        "Existing Create wrench pickup stopped working outside creature");
    var genericOrigin = origin.south(5);
    level.setBlock(genericOrigin, Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(genericOrigin.east(), flesh.defaultBlockState(), 3);
    level.setBlock(genericOrigin.west(), eyeState, 3);
    var generic =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            genericOrigin,
            List.of(genericOrigin, genericOrigin.east(), genericOrigin.west()),
            new BoundingBox3i(genericOrigin.west(), genericOrigin.east()));
    var genericCenter = generic.getPlot().getCenterBlock();
    helper.assertTrue(
        !CoreAssemblyRules.isCreature(generic), "Ordinary sublevel became a creature");
    for (var pos : List.of(genericCenter.east(), genericCenter.west())) {
      player.gameMode.useItemOn(
          player,
          level,
          player.getMainHandItem(),
          InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
      helper.assertTrue(
          level.getBlockState(pos).isAir(),
          "Wrench failed on Biomancy/own block in ordinary sublevel");
    }
    helper.assertTrue(
        !CoreAssemblyRules.isCreature(generic),
        "Wrench added creature identity to ordinary sublevel");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var click =
        new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock(
            player,
            core,
            Direction.NORTH,
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action
                .START);
    CombatAdapter.punch(click);
    helper.assertTrue(!click.isCanceled(), "Dead core still intercepts hand breaking as combat");
    var state = level.getBlockState(core);
    helper.assertTrue(
        state.getDestroyProgress(player, level, core) >= 1,
        "Dead core cannot be broken immediately by hand");
    helper.assertTrue(
        Block.getDrops(state, level, core, level.getBlockEntity(core), player, ItemStack.EMPTY)
            .isEmpty(),
        "Dead core has an item drop");
    helper.assertTrue(level.destroyBlock(core, true, player), "Dead core could not be removed");
    helper.runAfterDelay(10, helper::succeed);
  }
}
