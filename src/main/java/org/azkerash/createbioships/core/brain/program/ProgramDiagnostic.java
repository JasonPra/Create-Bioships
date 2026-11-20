package org.azkerash.createbioships.core.brain.program;

/** A source error safe to return to the editor without a Java stack trace. */
public record ProgramDiagnostic(int line, int column, String message) {
  @Override
  public String toString() {
    return "Line " + line + ", column " + column + ": " + message;
  }
}
