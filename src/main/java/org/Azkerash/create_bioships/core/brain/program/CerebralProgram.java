package org.Azkerash.create_bioships.core.brain.program;

import java.util.List;

/** Immutable internal instructions. Never deserialized from a client or item. */
public record CerebralProgram(List<Instruction> instructions, int locals) {
  public CerebralProgram {
    instructions = List.copyOf(instructions);
  }

  public enum Type {
    INT,
    DOUBLE,
    BOOL,
    STRING
  }

  public enum Op {
    PUSH,
    LOAD,
    STORE,
    TO_DOUBLE,
    POP,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    NEGATE,
    NOT,
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE,
    JUMP,
    FALSE,
    TRUE,
    PRINT,
    WAIT,
    STAT,
    HALT
  }

  public record Instruction(Op op, Object operand, int line, int column) {}
}
