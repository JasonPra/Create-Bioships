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
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east(), origin.west()),
            new BoundingBox3i(origin.west(), origin.east()));
    helper.assertTrue(body != null, "Wrench fixture assembly failed");
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelHealth.damage(body, 25, "test");
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wrench-test"));
    player.setShiftKeyDown(true);
    player.setItemInHand(
        InteractionHand.MAIN_HAND, Create_bioships.BIOSHIP_WRENCH.get().getDefaultInstance());
    var wrench = Create_bioships.BIOSHIP_WRENCH.get();
    for (var pos : List.of(core.east(), core.west())) {
      var context =
          new UseOnContext(
              player,
              InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
      helper.assertTrue(wrench.useOn(context).consumesAction(), "Create pickup was not handled");
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
