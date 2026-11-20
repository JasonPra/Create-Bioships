package org.Azkerash.create_bioships.core.brain.program.editor;

import java.util.*;
import java.util.regex.Pattern;
import org.Azkerash.create_bioships.core.brain.program.*;

/** Client preview diagnostics. The server still independently validates every compile/write. */
public final class EditorAnalysis {
  private static final Pattern LOCATION = Pattern.compile(".*?Line (\\d+), column (\\d+): (.*)");

  private EditorAnalysis() {}

  public static List<EditorProblem> analyze(String source, String fingerprint) {
    var compiled = ProgramCompiler.compile(source);
    var result = new ArrayList<EditorProblem>();
    if (!compiled.success()) {
      result.add(problem(EditorProblem.Severity.ERROR, compiled.diagnostic(), source, fingerprint));
    }
    for (var warning : compiled.warnings()) {
      result.add(problem(EditorProblem.Severity.WARNING, warning, source, fingerprint));
    }
    return List.copyOf(result);
  }

  private static EditorProblem problem(
      EditorProblem.Severity severity,
      ProgramDiagnostic diagnostic,
      String source,
      String fingerprint) {
    int offset = 0;
    for (int row = 1; row < diagnostic.line() && offset < source.length(); row++) {
      int next = source.indexOf('\n', offset);
      offset = next < 0 ? source.length() : next + 1;
    }
    offset = Math.min(source.length(), offset + Math.max(0, diagnostic.column() - 1));
    int end = offset;
    while (end < source.length()
        && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) {
      end++;
    }
    return new EditorProblem(
        severity,
        diagnostic.line(),
        diagnostic.column(),
        Math.max(1, end - offset),
        diagnostic.message(),
        fingerprint);
  }

  public static EditorProblem logged(
      EditorProblem.Severity severity, String message, String fingerprint) {
    var location = LOCATION.matcher(message);
    if (location.matches()) {
      try {
        return new EditorProblem(
            severity,
            Integer.parseInt(location.group(1)),
            Integer.parseInt(location.group(2)),
            1,
            location.group(3),
            fingerprint);
      } catch (NumberFormatException ignored) {
      }
    }
    return new EditorProblem(severity, 0, 0, 0, message, fingerprint);
  }
}
