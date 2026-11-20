package org.Azkerash.create_bioships.core.brain.program;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ProgramSwitchTests {
  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void switches_preserve_branch_scope_loop_control_and_sandbox_limits(
      GameTestHelper h) {
    ProgramSwitchChecks.run();
    h.succeed();
  }
}
