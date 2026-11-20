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

  static void accepts(GameTestHelper h, String source) {
    var result = ProgramCompiler.compile(source);
    h.assertTrue(result.success(), "Valid source rejected: " + result.diagnostic());
  }

  static void rejects(GameTestHelper h, String source) {
    h.assertTrue(
        !ProgramCompiler.compile(source).success(), "Unsafe/invalid source accepted: " + source);
  }
}
