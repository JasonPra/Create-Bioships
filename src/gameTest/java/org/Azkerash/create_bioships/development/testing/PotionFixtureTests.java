package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.CombatAdapter;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.core.CoreGogglesDisplay;
import org.Azkerash.create_bioships.core.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class PotionFixtureTests {
  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "visible_potion_fixture")
  public static void all_six_native_dispenser_cases_pass_and_reset(GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    state.setPotionOrigin(new BlockPos(768, 80, 128));
    PotionFixture.reset(level);
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(PotionFixture.dispenser(level)),
                    "Waiting for potion enclosure chunks"))
        .thenExecute(
            () -> {
              helper.assertTrue(PotionFixture.start(level) == 1, "Potion fixture did not start");
              checkCase(helper, 0);
            });
  }

  private static void checkCase(GameTestHelper helper, int index) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    if (index == 5) {
      helper
          .startSequence()
          .thenWaitUntil(
              () ->
                  helper.assertTrue(
                      !state.isPotionRunning(), "Waiting for baseline and protected zombie hits"))
          .thenExecute(
              () -> {
                helper.assertTrue(
                    state.getPotionResults().size() == 6
                        && state.getPotionResults().get(5).contains(": PASS")
                        && state.getPotionResults().get(5).contains("zombie damage"),
                    "Zombie resistance comparison failed: " + state.getPotionResults());
                helper.assertTrue(
                    state.getPotionZombie() == null, "Resistance zombie remained after test");
                finished(helper);
              });
      return;
    }
    helper.runAfterDelay(
        18,
        () -> {
          var body =
              (ServerSubLevel)
                  SubLevelContainer.getContainer(level).getSubLevel(state.getPotionBody());
          helper.assertTrue(body != null, "Potion subject lost");
          var target = CombatAdapter.target(body);
          var effect = PotionFixture.EFFECTS.get(index);
          if (!effect.value().isInstantenous()) {
            helper.assertTrue(target.hasEffect(effect), "Native dispenser missed effect " + effect);
          }
          var core = body.getPlot().getCenterBlock();
          var main = (CoreGogglesDisplay) level.getBlockEntity(core);
          var readout = (CoreGogglesDisplay) level.getBlockEntity(core.below().north());
          helper.assertTrue(
              main != null && readout != null, "Shared core/readout block entities missing");
          main.update();
          readout.update();
          helper.assertTrue(
              main.getUpdateTag(level.registryAccess())
                  .equals(readout.getUpdateTag(level.registryAccess())),
              "Core and Vital Readout snapshots disagree");
          helper.assertTrue(
              SubLevelData.hunger(body) == 5
                  && SubLevelData.hungerState(body).paused()
                  && SubLevelData.trust(body) == 0,
              "Placeholder defaults changed");
          for (int tick = 0; tick < 200; tick++) {
            target.tick();
          }
          state.setPotionDeadline(level.getGameTime());
          PotionFixture.tick(level);
          helper.assertTrue(
              state.getPotionResults().size() == index + 1
                  && state.getPotionResults().get(index).contains(": PASS"),
              "Potion case failed: " + state.getPotionResults());
          if (index + 1 < PotionFixture.EFFECTS.size()) {
            checkCase(helper, index + 1);
          } else {
            finished(helper);
          }
        });
  }

  private static void finished(GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    helper.assertTrue(
        !state.isPotionRunning() && state.getPotionBody() == null,
        "Finished suite did not restore non-started state");
    helper.assertTrue(
        level.getBlockState(PotionFixture.core(level)).is(ComponentRegistry.CORE.get()),
        "Reset did not restore sleeping core");
    helper.assertTrue(
        level.getBlockState(PotionFixture.dispenser(level).north()).is(Blocks.AIR),
        "Reset left dispenser powered");
    for (int x = 1; x <= 2; x++) {
      var pos = state.getPotionOrigin().offset(x, 0, -6);
      var door = level.getBlockState(pos);
      helper.assertTrue(
          door.is(DevelopmentTestBlocks.GLASS_DOOR.get())
              && level.getBlockState(pos.above()).is(DevelopmentTestBlocks.GLASS_DOOR.get()),
          "Double glass door missing");
      helper.assertTrue(
          DevelopmentTestBlocks.protectedBlock(door) && door.getDestroySpeed(level, pos) < 0,
          "Glass door was destructible");
      DevelopmentTestBlocks.GLASS_DOOR.get().setOpen(null, level, door, pos, true);
      helper.assertTrue(
          level.getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
          "Glass door cannot open");
    }
    helper.assertTrue(PotionFixture.start(level) == 1, "Potion test cannot run twice");
    PotionFixture.reset(level);
    helper.runAfterDelay(10, helper::succeed);
  }
}
