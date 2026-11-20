package org.Azkerash.create_bioships.core.brain.program;

import java.util.*;
import org.Azkerash.create_bioships.core.brain.program.CerebralProgram.*;
import org.Azkerash.create_bioships.core.brain.program.ProgramLexer.*;

/** Server-side, closed-world C# subset compiler. It has no reflection or class-loading path. */
public final class ProgramCompiler {
  public record Result(CerebralProgram program, ProgramDiagnostic diagnostic) {
    public boolean success() {
      return program != null;
    }
  }

  private record Local(int index, Type type) {}

  private final List<Token> tokens;
  private final List<Instruction> code = new ArrayList<>();
  private final Deque<Map<String, Local>> scopes = new ArrayDeque<>();

  private record Loop(int continueAt, List<Integer> breaks) {}

  private final Deque<Loop> loops = new ArrayDeque<>();
  private int cursor, locals, depth;

  private ProgramCompiler(List<Token> tokens) {
    this.tokens = tokens;
    scopes.push(new HashMap<>());
  }

  public static Result compile(String source) {
    try {
      return new Result(new ProgramCompiler(ProgramLexer.scan(source)).program(), null);
    } catch (Failure e) {
      return new Result(null, e.diagnostic());
    }
  }

  private CerebralProgram program() {
    if (match("using")) {
      expect("System");
      expect(";");
    }
    if (match("class")) {
      expect("Program");
      expect("{");
      match("public");
      expect("static");
      expect("void");
      expect("Main");
      expect("(");
      expect(")");
      block();
      expect("}");
    } else while (!atEnd()) statement();
    if (!atEnd()) fail(peek(), "Only one Program.Main entry point is supported.");
    emit(Op.HALT, null, peek());
    return new CerebralProgram(code, locals);
  }

  private void enter() {
    if (++depth > ProgramLimits.NESTING) fail(peek(), "Parser nesting limit exceeded.");
  }

  private void leave() {
    depth--;
  }

  private void block() {
    expect("{");
    scopes.push(new HashMap<>());
    while (!check("}") && !atEnd()) statement();
    expect("}");
    scopes.pop();
  }

  private void statement() {
    enter();
    try {
      if (check("{")) {
        block();
        return;
      }
      if (match(";")) return;
      if (match("if")) {
        conditional();
        return;
      }
      if (match("while")) {
        whileLoop();
        return;
      }
      if (match("for")) {
        forLoop();
        return;
      }
      if (match("break") || match("continue")) {
        Token jump = previous();
        if (loops.isEmpty()) fail(jump, "Loop control requires an enclosing loop.");
        expect(";");
        if (jump.text().equals("break")) loops.peek().breaks().add(emit(Op.JUMP, -1, jump));
        else emit(Op.JUMP, loops.peek().continueAt(), jump);
        return;
      }
      if (match("return")) {
        expect(";");
        emit(Op.HALT, null, previous());
        return;
      }
      simple();
      expect(";");
    } finally {
      leave();
    }
  }

  private void embedded() {
    if (Set.of("int", "double", "bool", "string", "var").contains(peek().text()))
      fail(peek(), "Put local declarations inside a block.");
    scopes.push(new HashMap<>());
    statement();
    scopes.pop();
  }

  private void conditional() {
    Token start = previous();
    expect("(");
    require(expression(), Type.BOOL, start);
    expect(")");
    int no = emit(Op.FALSE, -1, start);
    embedded();
    if (match("else")) {
      int end = emit(Op.JUMP, -1, start);
      patch(no, code.size());
      embedded();
      patch(end, code.size());
    } else patch(no, code.size());
  }

  private void whileLoop() {
    Token start = previous();
    int condition = code.size();
    expect("(");
    require(expression(), Type.BOOL, start);
    expect(")");
    int end = emit(Op.FALSE, -1, start);
    Loop loop = new Loop(condition, new ArrayList<>());
    loops.push(loop);
    embedded();
    emit(Op.JUMP, condition, start);
    loops.pop();
    finishLoop(loop, end);
  }

  private void forLoop() {
    Token start = previous();
    scopes.push(new HashMap<>());
    expect("(");
    if (!check(";")) simple();
    expect(";");
    int condition = code.size();
    if (!check(";")) require(expression(), Type.BOOL, start);
    else emit(Op.PUSH, true, start);
    expect(";");
    int end = emit(Op.FALSE, -1, start);
    int body = emit(Op.JUMP, -1, start);
    int increment = code.size();
    if (!check(")")) {
      if (Set.of("int", "double", "bool", "string", "var").contains(peek().text()))
        fail(peek(), "For increment cannot declare a variable.");
      simple();
    }
    expect(")");
    emit(Op.JUMP, condition, start);
    patch(body, code.size());
    Loop loop = new Loop(increment, new ArrayList<>());
    loops.push(loop);
    embedded();
    emit(Op.JUMP, increment, start);
    loops.pop();
    finishLoop(loop, end);
    scopes.pop();
  }

  private void finishLoop(Loop loop, int conditionExit) {
    patch(conditionExit, code.size());
    for (int exit : loop.breaks()) patch(exit, code.size());
  }

  private void simple() {
    if (Set.of("int", "double", "bool", "string", "var").contains(peek().text())) {
      Token declared = take(), name = name();
      if (reserved(name.text()) || scopes.stream().anyMatch(s -> s.containsKey(name.text())))
        fail(name, "Variable name is reserved or already declared.");
      expect("=");
      Type actual = expression();
      Type type = declared.text().equals("var") ? actual : type(declared);
      convert(actual, type, name);
      if (locals >= ProgramLimits.LOCALS) fail(name, "Local variable limit exceeded.");
      var local = new Local(locals++, type);
      scopes.peek().put(name.text(), local);
      emit(Op.STORE, local.index(), name);
      return;
    }
    if (peek().kind() == Kind.NAME
        && cursor + 1 < tokens.size()
        && Set.of("=", "+=", "-=", "*=", "/=", "%=", "++", "--")
            .contains(tokens.get(cursor + 1).text())) {
      Token name = take(), operator = take();
      Local local = local(name);
      if (!operator.text().equals("=")) emit(Op.LOAD, local.index(), name);
      Type value;
      if (operator.text().equals("++") || operator.text().equals("--")) {
        emit(Op.PUSH, 1, operator);
        value = binary(local.type(), Type.INT, operator.text().equals("++") ? "+" : "-", operator);
      } else {
        value = expression();
        if (!operator.text().equals("="))
          value = binary(local.type(), value, operator.text().substring(0, 1), operator);
      }
      convert(value, local.type(), name);
      emit(Op.STORE, local.index(), name);
      return;
    }
    Token start = peek();
    if (match("Console")) {
      expect(".");
      expect("WriteLine");
      expect("(");
      if (check(")")) emit(Op.PUSH, "", start);
      else expression();
      expect(")");
      emit(Op.PRINT, null, start);
      return;
    }
    if (match("Brain")) {
      expect(".");
      expect("Wait");
      expect("(");
      if (expression() != Type.INT) fail(start, "Brain.Wait requires an int tick count.");
      expect(")");
      emit(Op.WAIT, null, start);
      return;
    }
    fail(start, "Expected a declaration, assignment, or approved host call.");
  }

  private Type expression() {
    return or();
  }

  private Type or() {
    Type left = and();
    while (match("||")) {
      Token op = previous();
      require(left, Type.BOOL, op);
      int yes = emit(Op.TRUE, -1, op);
      require(and(), Type.BOOL, op);
      int end = emit(Op.JUMP, -1, op);
      patch(yes, code.size());
      emit(Op.PUSH, true, op);
      patch(end, code.size());
      left = Type.BOOL;
    }
    return left;
  }

  private Type and() {
    Type left = equality();
    while (match("&&")) {
      Token op = previous();
      require(left, Type.BOOL, op);
      int no = emit(Op.FALSE, -1, op);
      require(equality(), Type.BOOL, op);
      int end = emit(Op.JUMP, -1, op);
      patch(no, code.size());
      emit(Op.PUSH, false, op);
      patch(end, code.size());
      left = Type.BOOL;
    }
    return left;
  }

  private Type equality() {
    Type left = comparison();
    while (check("==") || check("!=")) {
      Token op = take();
      left = binary(left, comparison(), op.text(), op);
    }
    return left;
  }

  private Type comparison() {
    Type left = sum();
    while (Set.of("<", "<=", ">", ">=").contains(peek().text())) {
      Token op = take();
      left = binary(left, sum(), op.text(), op);
    }
    return left;
  }

  private Type sum() {
    Type left = product();
    while (check("+") || check("-")) {
      Token op = take();
      left = binary(left, product(), op.text(), op);
    }
    return left;
  }

  private Type product() {
    Type left = unary();
    while (check("*") || check("/") || check("%")) {
      Token op = take();
      left = binary(left, unary(), op.text(), op);
    }
    return left;
  }

  private Type unary() {
    enter();
    try {
      if (match("!") || match("-") || match("+")) {
        Token op = previous();
        Type value = unary();
        if (op.text().equals("!")) {
          require(value, Type.BOOL, op);
          emit(Op.NOT, null, op);
        } else {
          if (!numeric(value)) fail(op, "Unary arithmetic requires a number.");
          if (op.text().equals("-")) emit(Op.NEGATE, null, op);
        }
        return value;
      }
      return primary();
    } finally {
      leave();
    }
  }

  private Type primary() {
    Token token = take();
    if (token.kind() == Kind.NUMBER) {
      try {
        if (token.text().contains(".")) {
          double value = Double.parseDouble(token.text());
          if (!Double.isFinite(value)) fail(token, "Numeric literal is out of range.");
          emit(Op.PUSH, value, token);
          return Type.DOUBLE;
        }
        emit(Op.PUSH, Integer.parseInt(token.text()), token);
        return Type.INT;
      } catch (NumberFormatException e) {
        fail(token, "Numeric literal is out of range.");
      }
    }
    if (token.kind() == Kind.STRING) {
      emit(Op.PUSH, token.text(), token);
      return Type.STRING;
    }
    if (token.text().equals("true") || token.text().equals("false")) {
      emit(Op.PUSH, Boolean.parseBoolean(token.text()), token);
      return Type.BOOL;
    }
    if (token.text().equals("(")) {
      Type value = expression();
      expect(")");
      return value;
    }
    if (token.text().equals("Brain")) {
      expect(".");
      Token stat = name();
      if (!Set.of("Health", "Hunger", "Oxygen", "Tick").contains(stat.text()))
        fail(stat, "Unknown read-only brain statistic.");
      emit(Op.STAT, stat.text(), token);
      return Type.DOUBLE;
    }
    if (token.kind() == Kind.NAME) {
      Local local = local(token);
      emit(Op.LOAD, local.index(), token);
      return local.type();
    }
    fail(token, "Expected an expression.");
    return null;
  }

  private Type binary(Type left, Type right, String operator, Token token) {
    Op op =
        switch (operator) {
          case "+" -> Op.ADD;
          case "-" -> Op.SUB;
          case "*" -> Op.MUL;
          case "/" -> Op.DIV;
          case "%" -> Op.MOD;
          case "==" -> Op.EQ;
          case "!=" -> Op.NE;
          case "<" -> Op.LT;
          case "<=" -> Op.LE;
          case ">" -> Op.GT;
          case ">=" -> Op.GE;
          default -> throw new IllegalStateException("Unknown compiler operator");
        };
    boolean equality = op == Op.EQ || op == Op.NE;
    boolean comparison = equality || op == Op.LT || op == Op.LE || op == Op.GT || op == Op.GE;
    Type result;
    if (op == Op.ADD && (left == Type.STRING || right == Type.STRING)) result = Type.STRING;
    else if (numeric(left) && numeric(right))
      result =
          comparison
              ? Type.BOOL
              : left == Type.DOUBLE || right == Type.DOUBLE ? Type.DOUBLE : Type.INT;
    else if (equality && left == right) result = Type.BOOL;
    else {
      fail(token, "Operator is not valid for these types.");
      return null;
    }
    emit(op, null, token);
    return result;
  }

  private static boolean numeric(Type type) {
    return type == Type.INT || type == Type.DOUBLE;
  }

  private static Type type(Token token) {
    return Type.valueOf(token.text().toUpperCase(Locale.ROOT));
  }

  private void convert(Type actual, Type expected, Token token) {
    if (actual == Type.INT && expected == Type.DOUBLE) emit(Op.TO_DOUBLE, null, token);
    else require(actual, expected, token);
  }

  private void require(Type actual, Type expected, Token token) {
    if (actual != expected)
      fail(token, "Expected " + expected.name().toLowerCase(Locale.ROOT) + ".");
  }

  private Local local(Token token) {
    for (var scope : scopes) {
      var value = scope.get(token.text());
      if (value != null) return value;
    }
    fail(token, "Unknown local variable or unsupported API.");
    return null;
  }

  private static boolean reserved(String name) {
    return Set.of(
            "int",
            "double",
            "bool",
            "string",
            "var",
            "true",
            "false",
            "Brain",
            "Console",
            "System",
            "if",
            "else",
            "while",
            "for",
            "break",
            "continue",
            "return",
            "new",
            "class",
            "static",
            "void",
            "public",
            "using")
        .contains(name);
  }

  private int emit(Op op, Object operand, Token token) {
    if (code.size() >= ProgramLimits.INSTRUCTIONS) fail(token, "Instruction limit exceeded.");
    code.add(new Instruction(op, operand, token.line(), token.column()));
    return code.size() - 1;
  }

  private void patch(int index, int target) {
    var old = code.get(index);
    code.set(index, new Instruction(old.op(), target, old.line(), old.column()));
  }

  private Token name() {
    Token token = take();
    if (token.kind() != Kind.NAME) fail(token, "Expected a name.");
    return token;
  }

  private boolean match(String text) {
    if (!check(text)) return false;
    cursor++;
    return true;
  }

  private boolean check(String text) {
    return peek().text().equals(text);
  }

  private Token expect(String text) {
    Token token = take();
    if (!token.text().equals(text)) fail(token, "Expected '" + text + "'.");
    return token;
  }

  private Token take() {
    Token token = peek();
    if (!atEnd()) cursor++;
    return token;
  }

  private Token peek() {
    return tokens.get(cursor);
  }

  private Token previous() {
    return tokens.get(Math.max(0, cursor - 1));
  }

  private boolean atEnd() {
    return peek().kind() == Kind.END;
  }

  private static void fail(Token token, String message) {
    throw new Failure(token, message);
  }
}
