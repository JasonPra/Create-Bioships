package org.Azkerash.create_bioships.core.brain.program;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ProgramCompilerTests {
  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void expressions_are_typed_and_external_apis_are_rejected(GameTestHelper h) {
    accepts(
        h,
        "int a=2; double b=a+0.5; string s=\"x\"+b; bool ok=true && b>2; a+=3;"
            + " Console.WriteLine(s);");
    accepts(
        h,
        "using System; class Program { public static void Main() { Brain.Wait(20);"
            + " Console.WriteLine(Brain.Health); } }");
    rejects(h, "int a=true;");
    rejects(h, "int a=1.5;");
    rejects(h, "bool x=1;");
    rejects(h, "Console.WriteLine(missing);");
    rejects(h, "int a=1; int a=2;");
    rejects(h, "System.IO.File.Delete(\"x\");");
    rejects(h, "var x=new Process();");
    rejects(h, "Brain.Health=10;");
    rejects(h, "Brain.Wait(0.5);");
    rejects(h, "Console.WriteLine(" + "(".repeat(100) + "1" + ")".repeat(100) + ");");
    var error = ProgramCompiler.compile("int a=1;\nint b=true;");
    h.assertTrue(error.diagnostic().line() == 2, "Diagnostic lost source line");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void control_flow_is_scoped_and_bounded(GameTestHelper h) {
    accepts(h, ProgramCardData.TEMPLATE);
    accepts(h, "int n=0; while(n<10) { n++; if(n==2) continue; if(n==7) break; } return;");
    accepts(h, "for(int i=0;i<4;i++) { for(int j=0;j<2;j++) { if(j==0) continue; else break; } }");
    accepts(h, "for(;;) { Brain.Wait(10); }");
    rejects(h, "break;");
    rejects(h, "continue;");
    rejects(h, "if(1) return;");
    rejects(h, "for(int i=0;i<2;i++) {} Console.WriteLine(i);");
    rejects(h, "if(true) int x=1;");
    rejects(h, "{".repeat(80) + "}".repeat(80));
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "program_language", timeoutTicks = 20)
  public static void string_tokens_cannot_impersonate_syntax_and_malformed_programs_fail_closed(
      GameTestHelper h) {
    for (String source :
        java.util.List.of(
            "\"return\";",
            "\"Console\".WriteLine(1);",
            "int x=1 \"+\" 2;",
            "int x=1; x \"=\" 2;",
            "Console\".\"WriteLine(1);",
            "\"int\" x=1;",
            "int x=1 \"<\" 2;")) {
      rejects(h, source);
    }
    accepts(h, "Console.WriteLine(\"return\"); Console.WriteLine(-2147483648);");
    var random = new java.util.Random(31873);
    String[] words = {
      "int",
      "a",
      "=",
      "1",
      "true",
      "\"x\"",
      ";",
      "(",
      ")",
      "{",
      "}",
      "if",
      "else",
      "for",
      "while",
      "break",
      "continue",
      "return",
      "+",
      "++",
      "&&",
      "Brain",
      ".",
      "Wait",
      "Console",
      "WriteLine"
    };
    for (int n = 0; n < 5000; n++) {
      var source = new StringBuilder();
      int length = random.nextInt(100);
      for (int i = 0; i < length; i++) {
        source.append(words[random.nextInt(words.length)]).append(' ');
      }
      ProgramCompiler.compile(source.toString());
    }
    h.succeed();
  }

  static void accepts(GameTestHelper h, String source) {
    var result = ProgramCompiler.compile(source);
    h.assertTrue(result.success(), "Valid source rejected: " + result.diagnostic());
  }

  static void rejects(GameTestHelper h, String source) {
    h.assertTrue(
        !ProgramCompiler.compile(source).success(), "Unsafe/invalid source accepted: " + source);
  }
}
