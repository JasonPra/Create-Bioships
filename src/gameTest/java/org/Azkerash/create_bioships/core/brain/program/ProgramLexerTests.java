package org.Azkerash.create_bioships.core.brain.program;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ProgramLexerTests {
  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void tokens_preserve_positions_and_reject_malformed_input(GameTestHelper h) {
    var tokens = ProgramLexer.scan("// note\r\nint value = 2;\nConsole.WriteLine(\"ok\\n\");");
    h.assertTrue(
        tokens.getFirst().text().equals("int")
            && tokens.getFirst().line() == 2
            && tokens.getFirst().column() == 1,
        "CRLF source position lost");
    h.assertTrue(
        tokens.stream()
            .anyMatch(t -> t.kind() == ProgramLexer.Kind.STRING && t.text().equals("ok\n")),
        "Escaped literal was not decoded");
    reject(h, "/* unfinished", "Unterminated");
    reject(h, "\"unfinished", "Unterminated");
    reject(h, "\"bad\\q\"", "escape");
    reject(h, "[1]", "Unsupported");
    reject(h, "x ".repeat(2049), "token limit");
    reject(h, " ".repeat(8193), "8192");
    h.succeed();
  }

  private static void reject(GameTestHelper h, String source, String expected) {
    try {
      ProgramLexer.scan(source);
      throw new AssertionError("Accepted malformed source");
    } catch (ProgramLexer.Failure failure) {
      h.assertTrue(
          failure.diagnostic().message().contains(expected),
          "Unexpected diagnostic: " + failure.diagnostic());
    }
  }
}
