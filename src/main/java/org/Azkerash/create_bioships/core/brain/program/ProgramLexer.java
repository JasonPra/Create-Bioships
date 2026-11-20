package org.Azkerash.create_bioships.core.brain.program;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Deliberately small lexer: unsupported C# constructs fail closed in the parser. */
public final class ProgramLexer {
  public enum Kind {
    NAME,
    NUMBER,
    STRING,
    SYMBOL,
    END
  }

  public record Token(Kind kind, String text, int line, int column) {}

  public static final class Failure extends RuntimeException {
    private final ProgramDiagnostic diagnostic;

    public Failure(Token token, String message) {
      super(message, null, false, false);
      diagnostic = new ProgramDiagnostic(token.line(), token.column(), message);
    }

    public ProgramDiagnostic diagnostic() {
      return diagnostic;
    }
  }

  private static final Set<String> PAIRS =
      Set.of("==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "++", "--");
  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  private int cursor, line = 1, column = 1;

  private ProgramLexer(String source) {
    this.source = source;
  }

  public static List<Token> scan(String source) {
    if (source == null || source.length() > ProgramLimits.SOURCE)
      throw new Failure(new Token(Kind.END, "", 1, 1), "Source exceeds 8192 characters.");
    return new ProgramLexer(source).read();
  }

  private List<Token> read() {
    while (cursor < source.length()) {
      char c = peek(0);
      if (Character.isWhitespace(c)) {
        take();
        continue;
      }
      if (c == '/' && peek(1) == '/') {
        while (cursor < source.length() && peek(0) != '\n' && peek(0) != '\r') take();
        continue;
      }
      int row = line, col = column;
      if (c == '/' && peek(1) == '*') {
        take();
        take();
        while (cursor < source.length() && !(peek(0) == '*' && peek(1) == '/')) take();
        if (cursor == source.length()) fail(row, col, "Unterminated block comment.");
        take();
        take();
        continue;
      }
      if (isLetter(c) || c == '_') {
        int start = cursor;
        do {
          take();
        } while (isLetter(peek(0)) || isDigit(peek(0)) || peek(0) == '_');
        add(Kind.NAME, source.substring(start, cursor), row, col);
      } else if (isDigit(c)) {
        int start = cursor;
        while (isDigit(peek(0))) take();
        if (peek(0) == '.' && isDigit(peek(1))) {
          take();
          while (isDigit(peek(0))) take();
        }
        add(Kind.NUMBER, source.substring(start, cursor), row, col);
      } else if (c == '"') {
        take();
        StringBuilder value = new StringBuilder();
        while (cursor < source.length() && peek(0) != '"') {
          char next = take();
          if (next == '\n' || next == '\r') fail(row, col, "String literals cannot span lines.");
          if (next == '\\') {
            if (cursor == source.length()) fail(row, col, "Unterminated string escape.");
            next =
                switch (take()) {
                  case 'n' -> '\n';
                  case 'r' -> '\r';
                  case 't' -> '\t';
                  case '"' -> '"';
                  case '\\' -> '\\';
                  default -> throw error(row, col, "Unsupported string escape.");
                };
          }
          value.append(next);
          if (value.length() > ProgramLimits.STRING) fail(row, col, "String literal is too long.");
        }
        if (cursor == source.length()) fail(row, col, "Unterminated string literal.");
        take();
        add(Kind.STRING, value.toString(), row, col);
      } else {
        String pair = "" + c + peek(1);
        if (PAIRS.contains(pair)) {
          take();
          take();
          add(Kind.SYMBOL, pair, row, col);
        } else if ("{}();:,.+-*/%!=<>".indexOf(c) >= 0) {
          take();
          add(Kind.SYMBOL, "" + c, row, col);
        } else fail(row, col, "Unsupported character.");
      }
    }
    tokens.add(new Token(Kind.END, "", line, column));
    return List.copyOf(tokens);
  }

  private void add(Kind kind, String text, int row, int col) {
    if (tokens.size() >= ProgramLimits.TOKENS) fail(row, col, "Program exceeds token limit.");
    tokens.add(new Token(kind, text, row, col));
  }

  private char peek(int offset) {
    return cursor + offset < source.length() ? source.charAt(cursor + offset) : '\0';
  }

  private char take() {
    char c = source.charAt(cursor++);
    if (c == '\r') {
      line++;
      column = 1;
    } else if (c == '\n') {
      if (cursor < 2 || source.charAt(cursor - 2) != '\r') line++;
      column = 1;
    } else column++;
    return c;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isLetter(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
  }

  private static Failure error(int line, int col, String message) {
    return new Failure(new Token(Kind.END, "", line, col), message);
  }

  private static void fail(int line, int col, String message) {
    throw error(line, col, message);
  }
}
