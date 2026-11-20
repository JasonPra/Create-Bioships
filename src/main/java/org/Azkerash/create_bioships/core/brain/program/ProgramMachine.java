package org.Azkerash.create_bioships.core.brain.program;

import java.util.*;
import org.Azkerash.create_bioships.core.brain.program.CerebralProgram.*;

/** Cooperative, in-memory interpreter. No instruction can invoke arbitrary host code. */
public final class ProgramMachine {
  public interface Host {
    /** Only Health, Hunger, Oxygen and Tick are emitted by the compiler. */
    double statistic(String name);
  }

  public enum State {
    READY,
    WAITING,
    COMPLETED,
    FAULTED,
    STOPPED
  }

  private final CerebralProgram program;
  private final Object[] locals;
  private final Object[] stack = new Object[ProgramLimits.STACK];
  private final Deque<String> output = new ArrayDeque<>();
  private int pc, top;
  private long resumeAt;
  private State state = State.READY;
  private ProgramDiagnostic fault;

  public ProgramMachine(CerebralProgram program) {
    if (program == null
        || program.locals() < 0
        || program.locals() > ProgramLimits.LOCALS
        || program.instructions().size() > ProgramLimits.INSTRUCTIONS)
      throw new IllegalArgumentException("Invalid compiled program");
    this.program = program;
    locals = new Object[program.locals()];
  }

  public State state() {
    return state;
  }

  public ProgramDiagnostic fault() {
    return fault;
  }

  public List<String> output() {
    return List.copyOf(output);
  }

  public void stop() {
    state = State.STOPPED;
    Arrays.fill(locals, null);
    Arrays.fill(stack, null);
    top = 0;
  }

  /** Called by the server at brain cadence, never recursively or from script threads. */
  public void advance(long gameTick, Host host) {
    if (state == State.COMPLETED || state == State.FAULTED || state == State.STOPPED) return;
    if (state == State.WAITING && gameTick < resumeAt) return;
    state = State.READY;
    Instruction current = null;
    try {
      for (int steps = 0; steps < ProgramLimits.STEPS_PER_UPDATE; steps++) {
        if (pc < 0 || pc >= program.instructions().size())
          throw new Fault("Invalid instruction address.");
        current = program.instructions().get(pc++);
        switch (current.op()) {
          case PUSH -> push(current.operand());
          case LOAD -> {
            Object value = locals[(int) current.operand()];
            if (value == null) throw new Fault("Variable was not initialized.");
            push(value);
          }
          case STORE -> locals[(int) current.operand()] = pop();
          case TO_DOUBLE -> push(((Number) pop()).doubleValue());
          case POP -> pop();
          case NEGATE -> {
            Object value = pop();
            if (value instanceof Integer n) push(-n);
            else push(-((Double) value));
          }
          case NOT -> push(!((Boolean) pop()));
          case ADD, SUB, MUL, DIV, MOD, EQ, NE, LT, LE, GT, GE -> binary(current.op());
          case JUMP -> pc = (int) current.operand();
          case FALSE -> {
            if (!((Boolean) pop())) pc = (int) current.operand();
          }
          case TRUE -> {
            if ((Boolean) pop()) pc = (int) current.operand();
          }
          case PRINT -> print(display(pop()));
          case STAT -> {
            double value = host.statistic((String) current.operand());
            if (!Double.isFinite(value)) throw new Fault("Statistic is not finite.");
            push(value);
          }
          case WAIT -> {
            int ticks = (Integer) pop();
            if (ticks < 0 || ticks > ProgramLimits.MAX_WAIT)
              throw new Fault("Wait must be between 0 and 72000 ticks.");
            long delay = Math.max(1, ticks);
            resumeAt = gameTick > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTick + delay;
            state = State.WAITING;
            return;
          }
          case HALT -> {
            state = State.COMPLETED;
            return;
          }
        }
      }
      throw new Fault("Instruction budget exhausted. Add Brain.Wait to long-running loops.");
    } catch (Fault | ArithmeticException | ClassCastException | IndexOutOfBoundsException e) {
      String message = e instanceof Fault ? e.getMessage() : "Invalid arithmetic or machine state.";
      fault =
          new ProgramDiagnostic(
              current == null ? 1 : current.line(),
              current == null ? 1 : current.column(),
              message);
      state = State.FAULTED;
      Arrays.fill(stack, null);
      top = 0;
    }
  }

  private void binary(Op op) {
    Object right = pop(), left = pop();
    if (op == Op.ADD && (left instanceof String || right instanceof String)) {
      String a = display(left), b = display(right);
      if (a.length() + b.length() > ProgramLimits.STRING) throw new Fault("String limit exceeded.");
      push(a + b);
      return;
    }
    if (op == Op.EQ || op == Op.NE) {
      boolean equal;
      if (left instanceof Number a && right instanceof Number b)
        equal = a.doubleValue() == b.doubleValue();
      else equal = left.equals(right);
      push(op == Op.EQ ? equal : !equal);
      return;
    }
    Number a = (Number) left, b = (Number) right;
    double x = a.doubleValue(), y = b.doubleValue();
    switch (op) {
      case LT -> push(x < y);
      case LE -> push(x <= y);
      case GT -> push(x > y);
      case GE -> push(x >= y);
      default -> {
        if ((op == Op.DIV || op == Op.MOD) && y == 0) throw new Fault("Division by zero.");
        if (left instanceof Integer && right instanceof Integer) {
          int i = a.intValue(), j = b.intValue();
          if (op == Op.DIV && i == Integer.MIN_VALUE && j == -1)
            throw new Fault("Integer division overflow.");
          int value =
              switch (op) {
                case ADD -> i + j;
                case SUB -> i - j;
                case MUL -> i * j;
                case DIV -> i / j;
                case MOD -> i % j;
                default -> throw new Fault("Invalid arithmetic instruction.");
              };
          push(value);
        } else {
          double value =
              switch (op) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MOD -> x % y;
                default -> throw new Fault("Invalid arithmetic instruction.");
              };
          if (!Double.isFinite(value)) throw new Fault("Numeric result is not finite.");
          push(value);
        }
      }
    }
  }

  private void push(Object value) {
    if (top >= stack.length) throw new Fault("Evaluation stack limit exceeded.");
    if (value instanceof String s && s.length() > ProgramLimits.STRING)
      throw new Fault("String limit exceeded.");
    stack[top++] = value;
  }

  private Object pop() {
    if (top == 0) throw new Fault("Evaluation stack underflow.");
    Object value = stack[--top];
    stack[top] = null;
    return value;
  }

  private static String display(Object value) {
    if (value instanceof Boolean b) return b ? "True" : "False";
    return value.toString();
  }

  private void print(String value) {
    // Split only within the already bounded string, retaining a bounded history.
    for (String line : value.split("\\R", -1)) {
      if (output.size() == ProgramLimits.OUTPUT_LINES) output.removeFirst();
      String safe = line.replaceAll("[\\p{Cntrl}&&[^\\t]]", "");
      output.addLast(safe.substring(0, Math.min(safe.length(), ProgramLimits.OUTPUT_LINE)));
    }
  }

  private static final class Fault extends RuntimeException {
    Fault(String message) {
      super(message, null, false, false);
    }
  }
}
