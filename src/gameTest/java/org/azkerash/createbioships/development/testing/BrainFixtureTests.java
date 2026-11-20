package org.azkerash.createbioships.development.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.core.brain.SubLevelBrains;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class BrainFixtureTests {
  @GameTest(template = "test_empty", batch = "visible_brain_and_chewing", timeoutTicks = 150)
  public static void visible_brain_and_mouth_start_feed_reset(GameTestHelper helper) {
    var level = helper.getLevel();
    BrainFixture.reset(level);
    helper.assertTrue(
        BrainFixture.start(level) == 1 && BrainFixture.start(level) == 0,
        "START duplicated or failed subject");
    var body = BrainFixture.body(level);
    var brain = SubLevelBrains.get(body);
    helper.assertTrue(
        brain.capacity() == 27 && !brain.inventory().getItem(0).isEmpty(),
        "Fixture did not seed full brain and blank card");
    helper.assertTrue(BrainFixture.feed(level) == 1, "FEED did not accept test meal");
    var pos = body.getPlot().getCenterBlock().north();
    helper.assertTrue(
        ((MouthBlockEntity) level.getBlockEntity(pos)).chewRemaining() == 60,
        "Fixture meal did not animate mouth");
    helper.runAfterDelay(
        65,
        () -> {
          helper.assertTrue(
              level.getBlockState(pos).getValue(MouthBlock.CHEW) == 0,
              "Fixture mouth did not close after chewing");
          BrainFixture.reset(level);
          helper.assertTrue(
              body.isRemoved() && BrainFixture.body(level) == null, "RESET retained old subject");
          helper.assertTrue(BrainFixture.start(level) == 1, "RESET prevented repeat START");
          BrainFixture.reset(level);
          helper.succeed();
        });
  }
}
