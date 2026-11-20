package org.azkerash.createbioships.core.brain.program;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class SourceEditorTests {
  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void editing_preserves_source_folding_indentation_and_warning_locations(
      GameTestHelper h) {
    SourceEditorChecks.run();
    h.succeed();
  }
}
