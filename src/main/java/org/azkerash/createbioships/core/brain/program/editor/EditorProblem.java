package org.azkerash.createbioships.core.brain.program.editor;

/** Source-versioned presentation data; historical diagnostics never masquerade as current ones. */
public record EditorProblem(
    Severity severity, int line, int column, int length, String message, String fingerprint) {
  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public EditorProblem {
    message =
        message.substring(
            0,
            Math.min(
                org.azkerash.createbioships.core.brain.program.ProgramLimits.DIAGNOSTIC_CHARS,
                message.length()));
  }

  public String label() {
    return (line > 0 ? "L" + line + ":" + column + " · " : "") + message;
  }
}
