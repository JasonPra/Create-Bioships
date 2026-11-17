package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.core.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CombatFixtureTests {
  @GameTest(template = "test_empty", timeoutTicks = 200, batch = "visible_combat_regression")
  public static void visible_combat_suite_advances_and_restores_unstarted_state(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    state.combatOrigin = helper.absolutePos(new BlockPos(0, 3, 0));
    CombatFixture.reset(level);
    var core = state.combatOrigin.above(9);
    helper.assertTrue(
        level.getBlockState(core).is(Create_bioships.CORE.get())
            && level.getBlockState(core).getValue(CoreBlock.BLINK),
        "Reset assembled subject prematurely");
    helper.assertTrue(
        level
            .getBlockState(state.combatOrigin.offset(13, 2, 0))
            .is(DevelopmentTestBlocks.GLASS.get()),
        "Indestructible glass wall missing");
    helper.assertTrue(
        level
            .getBlockState(state.combatOrigin.above(CombatFixture.HEIGHT))
            .is(DevelopmentTestBlocks.GLASS.get()),
        "Indestructible roof missing");
    helper.assertTrue(
        level.getBlockState(state.combatOrigin.below()).is(Blocks.BEDROCK),
        "Combat floor is destructible");
    helper.assertTrue(
        DevelopmentTestBlocks.GLASS.get().asItem() == net.minecraft.world.item.Items.AIR,
        "Development glass has an obtainable item");
    helper.assertTrue(
        DevelopmentTestBlocks.GLASS.get().defaultBlockState().getDestroySpeed(level, core) < 0,
        "Development glass can be mined");
    helper.runAfterDelay(
        5,
        () -> {
          var start =
              (CommandBlockEntity) level.getBlockEntity(state.combatOrigin.offset(-3, 0, -16));
          start.getCommandBlock().performCommand(level);
          helper.assertTrue(
              state.combatRunning && !state.combatEnemies.isEmpty(),
              "Start did not enumerate enemies");
          var body =
              (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(state.combatBody);
          helper.assertTrue(
              body != null && SubLevelHealth.status(body).current() == 1,
              "Subject not assembled at one HP");
          var first = body.getUniqueId();
          SubLevelHealth.damage(body, 1, "regression");
          CombatFixture.tick(level);
          helper.assertTrue(
              state.combatIndex == 1 && !first.equals(state.combatBody),
              "Death did not advance to a fresh subject");
          // Finish the suite without waiting for every enemy's 60-second observation window.
          state.combatIndex = state.combatEnemies.size() - 1;
          body =
              (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(state.combatBody);
          SubLevelHealth.damage(body, 1, "regression");
          CombatFixture.tick(level);
          helper.assertTrue(
              !state.combatRunning && state.combatBody == null,
              "Completed suite did not reset automatically");
          helper.assertTrue(
              level.getBlockState(core).is(Create_bioships.CORE.get())
                  && level.getBlockState(core).getValue(CoreBlock.BLINK),
              "Final reset did not restore sleeping subject");
          helper.assertTrue(state.combatResults.size() == 2, "Reset discarded results");
          var reset =
              (CommandBlockEntity) level.getBlockEntity(state.combatOrigin.offset(3, 0, -16));
          reset.getCommandBlock().performCommand(level);
          reset.getCommandBlock().performCommand(level);
          helper.assertTrue(
              !state.combatRunning && state.combatResults.size() == 2,
              "Manual reset lost saved results");
          helper.succeed();
        });
  }
}
