package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class DigestionFixtureTests {
  @GameTest(template = "test_empty", timeoutTicks = 1800, batch = "visible_digestion_fixture")
  public static void visible_fixture_passes_native_digestion_and_can_be_reset(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    state.digestionOrigin = new BlockPos(1344, 80, 128);
    DigestionFixture.reset(level);
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(DigestionFixture.mouth(level)),
                    "Waiting for fixture chunks"))
        .thenExecute(
            () ->
                helper.assertTrue(
                    DigestionFixture.start(level) == 1, "Digestion fixture failed to start"))
        .thenWaitUntil(
            () -> helper.assertTrue(!state.digestionRunning, "Waiting for four native meals"))
        .thenExecute(
            () -> {
              helper.assertTrue(
                  DigestionFixture.status(level).startsWith("PASS:"),
                  DigestionFixture.status(level));
              DigestionFixture.reset(level);
              helper.assertTrue(
                  state.digestionBody == null && !state.digestionRunning, "Reset retained subject");
              helper.assertTrue(DigestionFixture.start(level) == 1, "Fixture could not restart");
              DigestionFixture.reset(level);
            })
        .thenExecuteAfter(10, helper::succeed);
  }
}
