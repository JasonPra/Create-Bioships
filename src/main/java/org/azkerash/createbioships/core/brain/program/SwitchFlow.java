package org.azkerash.createbioships.core.brain.program;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import org.azkerash.createbioships.core.brain.program.CerebralProgram.Instruction;

/** Iterative, instruction-bounded check that a switch section cannot fall into the next one. */
final class SwitchFlow {
  private SwitchFlow() {}

  static boolean canReachEnd(List<Instruction> code, int start, int end) {
    var pending = new ArrayDeque<Integer>();
    var visited = new HashSet<Integer>();
    pending.add(start);
    while (!pending.isEmpty()) {
      int address = pending.removeFirst();
      if (address == end) {
        return true;
      }
      // Unpatched break jumps and outer-loop continues leave the section.
      if (address < start || address > end || !visited.add(address)) {
        continue;
      }
      var instruction = code.get(address);
      switch (instruction.op()) {
        case HALT -> {}
        case JUMP -> pending.add((Integer) instruction.operand());
        case FALSE, TRUE -> {
          pending.add((Integer) instruction.operand());
          pending.add(address + 1);
        }
        default -> pending.add(address + 1);
      }
    }
    return false;
  }
}
