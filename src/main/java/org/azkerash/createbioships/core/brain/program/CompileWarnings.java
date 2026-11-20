package org.azkerash.createbioships.core.brain.program;

import java.util.*;
import org.azkerash.createbioships.core.brain.program.ProgramLexer.Token;

/** Non-blocking, bounded source warnings, separate from executable validation. */
final class CompileWarnings {
  private static final int MAX_WARNING_NAME_CHARS = 48;

  record Declaration(int index, Token token) {}

  private CompileWarnings() {}

  static List<ProgramDiagnostic> analyze(
      CerebralProgram program, List<Declaration> declarations, Set<Integer> reads) {
    var warnings = new ArrayList<ProgramDiagnostic>();
    for (var declaration : declarations) {
      if (!reads.contains(declaration.index()) && warnings.size() < ProgramLimits.WARNINGS) {
        var token = declaration.token();
        warnings.add(
            new ProgramDiagnostic(
                token.line(),
                token.column(),
                "Local '"
                    + token
                        .text()
                        .substring(0, Math.min(MAX_WARNING_NAME_CHARS, token.text().length()))
                    + "' is never read."));
      }
    }
    var code = program.instructions();
    var visited = new BitSet(code.size());
    var pending = new ArrayDeque<Integer>();
    pending.add(0);
    while (!pending.isEmpty()) {
      int index = pending.removeFirst();
      if (index < 0 || index >= code.size() || visited.get(index)) {
        continue;
      }
      visited.set(index);
      var instruction = code.get(index);
      switch (instruction.op()) {
        case HALT -> {}
        case JUMP -> pending.add((Integer) instruction.operand());
        case TRUE, FALSE -> {
          pending.add((Integer) instruction.operand());
          pending.add(index + 1);
        }
        default -> pending.add(index + 1);
      }
    }
    var lines = new HashSet<Integer>();
    for (int i = 0; i < code.size() && warnings.size() < ProgramLimits.WARNINGS; i++) {
      var instruction = code.get(i);
      if (visited.get(i)) {
        continue;
      }
      switch (instruction.op()) {
        case PUSH, LOAD, STORE, PRINT, WAIT, STAT -> {
          if (lines.add(instruction.line())) {
            warnings.add(
                new ProgramDiagnostic(
                    instruction.line(), instruction.column(), "Unreachable code."));
          }
        }
        default -> {}
      }
    }
    warnings.sort(
        Comparator.comparingInt(ProgramDiagnostic::line)
            .thenComparingInt(ProgramDiagnostic::column));
    return List.copyOf(warnings);
  }
}
