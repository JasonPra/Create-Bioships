package org.azkerash.createbioships.core.brain.program;

import java.util.*;
import org.azkerash.createbioships.core.brain.program.editor.*;

/** Pure source editing checks; no Minecraft client or graphics context is required. */
public final class SourceEditorChecks {
  public static void main(String[] args) {
    run();
    System.out.println("CEREBRAL_SOURCE_EDITOR_CHECKS_PASS");
  }

  public static void run() {
    completionChecks();
    var document = new SourceDocument("if (true) {}");
    document.move(document.text().length() - 1, false);
    document.enter();
    check(
        document.text().equals("if (true) {\n    \n}"),
        "Brace pair did not create aligned nested lines");
    document.insert("if (true) ");
    document.type('{');
    document.enter();
    check(
        document.text().contains("    if (true) {\n        \n    }"),
        "Nested block did not increase indentation");
    check(document.column() == 8, "Caret did not enter nested indentation");
    String nested = document.text();
    document.undo();
    document.redo();
    check(document.text().equals(nested), "Indentation was not undoable");
    var close = new SourceDocument("if(true){\n        ");
    close.type('}');
    check(close.text().equals("if(true){\n}"), "Closing brace was not aligned");
    var tabs = new SourceDocument("one\ntwo\n");
    tabs.selectAll();
    tabs.indent(false);
    check(tabs.text().equals("    one\n    two\n"), "Selection indent changed trailing lines");
    tabs.indent(true);
    check(tabs.text().equals("one\ntwo\n"), "Selection outdent lost text");
    var back = new SourceDocument("        ");
    back.backspace();
    check(back.text().equals("    "), "Smart backspace did not remove one indent");
    var fold =
        new SourceDocument(
            "if(true){\n"
                + "    if(false){\n"
                + "        Console.WriteLine(1);\n"
                + "    }\n"
                + "}\n"
                + "Console.WriteLine(2);");
    String original = fold.text();
    fold.toggleFold(1);
    fold.toggleFold(0);
    check(
        fold.visibleLines().equals(List.of(0, 4, 5)), "Outer fold did not hide only its interior");
    check(fold.text().equals(original), "Folding changed saved program source");
    fold.toggleFold(0);
    check(!fold.visibleLines().contains(2), "Expanding outer fold lost nested fold state");
    fold.move(fold.lineStart(2), false);
    check(fold.visibleLines().contains(2), "Diagnostic navigation did not reveal hidden line");
    fold.toggleFold(0);
    fold.move(fold.lineStart(2), false);
    check(
        fold.lineAt(fold.caret()) == 2 && !fold.collapsed(0), "Caret remained inside hidden code");
    var syntax = new SourceSyntax("// {\nConsole.WriteLine(\"{ }\"); /* {\n } */\nif(true){\n}\n");
    check(syntax.blocks().size() == 1, "Comments or strings created folding regions");
    check(
        syntax.spans().stream().anyMatch(s -> s.kind() == SourceSyntax.Kind.METHOD),
        "Method highlighting missing");
    var comment = new SourceDocument("// {");
    comment.enter();
    check(comment.column() == 0, "Comment brace affected indentation");
    var partial = new SourceDocument("if(true){\n    Console.WriteLine(\"unfinished {");
    partial.enter();
    check(partial.column() == 4, "Unfinished string affected structural indentation");
    var limited = new SourceDocument("x".repeat(8190));
    limited.insert("abcd");
    check(limited.text().length() == 8192, "Editor bypassed source limit");
    limited.selectAll();
    limited.insert("fresh");
    check(limited.text().equals("fresh"), "Replace selection did not release source capacity");
    var errors = EditorAnalysis.analyze("Console.WriteLine(missing);", "source-v1");
    check(
        errors.size() == 1
            && errors.getFirst().severity() == EditorProblem.Severity.ERROR
            && errors.getFirst().length() == 7
            && errors.getFirst().fingerprint().equals("source-v1"),
        "Error tracker lost name range or source version");
    var history = new EditorLog();
    for (int i = 0; i < 100; i++) {
      history.add(
          EditorAnalysis.logged(
              EditorProblem.Severity.ERROR, "Line 2, column 9: Error " + i, "source-v1"));
    }
    check(
        history.entries().size() == 64
            && history.entries().getFirst().message().equals("Error 99")
            && history.entries().getFirst().line() == 2,
        "Error history was not bounded or lost source location");
    history.clear();
    check(history.entries().isEmpty(), "Error log could not be cleared");
    var green = EditorAnalysis.analyze("int unused=1;", "source-v2");
    check(
        green.size() == 1
            && green.getFirst().severity() == EditorProblem.Severity.WARNING
            && green.getFirst().column() == 5,
        "Warning tracker lost declaration location");
    var warning = ProgramCompiler.compile("int unused=1;\nreturn;\nConsole.WriteLine(3);");
    check(
        warning.success()
            && warning.warnings().stream()
                .anyMatch(w -> w.message().contains("never read") && w.line() == 1),
        "Unused-local warning missing");
    check(
        warning.warnings().stream()
            .anyMatch(w -> w.message().contains("Unreachable") && w.line() == 3),
        "Unreachable-code warning missing");
    var used = ProgramCompiler.compile("int value=2;Console.WriteLine(value);");
    check(used.warnings().isEmpty(), "Read local reported unused");
    check(
        ProgramCompiler.compile("return;").warnings().isEmpty(),
        "Generated halt reported as unreachable source");
    var switched =
        ProgramCompiler.compile("switch(1){case 1:Console.WriteLine(1);break;default:break;}");
    check(switched.warnings().isEmpty(), "Switch dispatch generated a false warning");
    var error = ProgramCompiler.compile("Console.WriteLine(missing);");
    check(
        !error.success() && error.diagnostic().column() > 0, "Unknown name lost its error marker");
  }

  private static void completionChecks() {
    var call = new SourceDocument("Console.Wri");
    var matches = SourceCompletion.suggest(call, false);
    check(matches.suggestions().size() == 1, "Console API suggestion missing");
    SourceCompletion.accept(call, matches, 0);
    check(
        call.text().equals("Console.WriteLine()") && call.column() == 18,
        "Completion did not place caret inside arguments");
    call.undo();
    check(call.text().equals("Console.Wri"), "Completion was not a single undoable edit");
    var stats = SourceCompletion.suggest(new SourceDocument("Brain."), false);
    check(stats.suggestions().size() == 5, "Approved Brain API inventory changed");
    check(
        SourceCompletion.suggest(new SourceDocument("System."), true).suggestions().isEmpty(),
        "Completion advertised an unsupported host API");
    for (String source : List.of("// Console.Wri", "/* Brain.", "\"Brain.")) {
      check(
          SourceCompletion.suggest(new SourceDocument(source), true).suggestions().isEmpty(),
          "Completion opened inside a comment or string");
    }
    var local = new SourceDocument("int pulse=0;\npul");
    check(
        SourceCompletion.suggest(local, false).suggestions().stream()
            .anyMatch(value -> value.text().equals("pulse")),
        "Local declaration not suggested");
    var middle = new SourceDocument("Brain.HealZZ");
    middle.move(10, false);
    var replacement = SourceCompletion.suggest(middle, false);
    SourceCompletion.accept(middle, replacement, 0);
    check(middle.text().equals("Brain.Health"), "Completion left a stale identifier suffix");
    local.selectAll();
    check(
        SourceCompletion.suggest(local, true).suggestions().isEmpty(),
        "Completion overwrote an active selection");
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
