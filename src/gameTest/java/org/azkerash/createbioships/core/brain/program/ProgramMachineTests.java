package org.azkerash.createbioships.core.brain.program;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ProgramMachineTests {
  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void interpreter_preserves_control_flow_and_enforces_runtime_limits(
      GameTestHelper h) {
    ProgramMachineChecks.run();
    h.succeed();
  }
}
