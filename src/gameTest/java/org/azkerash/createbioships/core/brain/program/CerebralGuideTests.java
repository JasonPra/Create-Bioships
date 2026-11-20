package org.azkerash.createbioships.core.brain.program;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class CerebralGuideTests {
  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void guide_examples_compile_and_execute_in_the_sandbox(GameTestHelper helper)
      throws Exception {
    CerebralGuideChecks.run();
    var book = org.azkerash.createbioships.CreateBioships.CEREBRAL_CODEX.get().getDefaultInstance();
    helper.assertTrue(
        !book.isEmpty() && book.getMaxStackSize() == 1, "Codex item was not registered");
    helper.succeed();
  }
}
