package org.Azkerash.create_bioships.core.brain.program.editor;

import java.util.*;

/** Tolerant lexical presentation for incomplete source; it never authorizes execution. */
public final class SourceSyntax {
  public enum Kind {
    NAME,
    KEYWORD,
    TYPE,
    METHOD,
    PROPERTY,
    NUMBER,
    STRING,
    COMMENT,
    SYMBOL
  }

  public record Span(int start, int end, Kind kind, boolean openEnd) {}

  public record Brace(int offset, boolean opening) {}

  public record Block(int start, int end) {}

  private static final Set<String> KEYWORDS =
      Set.of(
          "using",
          "class",
          "public",
          "static",
          "void",
          "if",
          "else",
          "while",
          "for",
          "switch",
          "case",
          "default",
          "break",
          "continue",
          "return",
          "true",
          "false",
          "new");
  private static final Set<String> TYPES =
      Set.of("int", "double", "bool", "string", "var", "Brain", "Console", "System", "Program");
  private static final Set<String> PROPERTIES = Set.of("Health", "Hunger", "Oxygen", "Tick");
  private final List<Span> spans;
  private final List<Brace> braces;
  private final List<Block> blocks;

  public SourceSyntax(String source) {
    var spans = new ArrayList<Span>();
    var braces = new ArrayList<Brace>();
    var blocks = new ArrayList<Block>();
    var open = new ArrayDeque<Integer>();
    int i = 0;
    while (i < source.length()) {
      int start = i;
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
        i += 2;
        while (i < source.length() && source.charAt(i) != '\n') {
          i++;
        }
        spans.add(new Span(start, i, Kind.COMMENT, true));
        continue;
      }
      if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
        i += 2;
        while (i < source.length()
            && !(source.charAt(i) == '*'
                && i + 1 < source.length()
                && source.charAt(i + 1) == '/')) {
          i++;
        }
        boolean closed = i < source.length();
        if (closed) {
          i += 2;
        }
        spans.add(new Span(start, i, Kind.COMMENT, !closed));
        continue;
      }
      if (c == '"') {
        i++;
        boolean closed = false;
        while (i < source.length() && source.charAt(i) != '\n') {
          char next = source.charAt(i++);
          if (next == '\\' && i < source.length() && source.charAt(i) != '\n') {
            i++;
          } else if (next == '"') {
            closed = true;
            break;
          }
        }
        spans.add(new Span(start, i, Kind.STRING, !closed));
        continue;
      }
      if (Character.isLetter(c) || c == '_') {
        i++;
        while (i < source.length()
            && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
          i++;
        }
        String word = source.substring(start, i);
        int next = i;
        while (next < source.length() && Character.isWhitespace(source.charAt(next))) {
          next++;
        }
        Kind kind =
            KEYWORDS.contains(word)
                ? Kind.KEYWORD
                : TYPES.contains(word)
                    ? Kind.TYPE
                    : next < source.length() && source.charAt(next) == '('
                        ? Kind.METHOD
                        : PROPERTIES.contains(word) ? Kind.PROPERTY : Kind.NAME;
        spans.add(new Span(start, i, kind, false));
        continue;
      }
      if (Character.isDigit(c)) {
        i++;
        while (i < source.length()
            && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) {
          i++;
        }
        spans.add(new Span(start, i, Kind.NUMBER, false));
        continue;
      }
      if (c == '{') {
        braces.add(new Brace(i, true));
        open.push(i);
      } else if (c == '}') {
        braces.add(new Brace(i, false));
        if (!open.isEmpty()) {
          blocks.add(new Block(open.pop(), i));
        }
      }
      spans.add(new Span(i, ++i, Kind.SYMBOL, false));
    }
    this.spans = List.copyOf(spans);
    this.braces = List.copyOf(braces);
    this.blocks = List.copyOf(blocks);
  }

  public List<Span> spans() {
    return spans;
  }

  public List<Block> blocks() {
    return blocks;
  }

  public boolean codeAt(int offset) {
    for (var span : spans) {
      if ((span.kind() == Kind.COMMENT || span.kind() == Kind.STRING)
          && offset > span.start()
          && (offset < span.end() || offset == span.end() && span.openEnd())) {
        return false;
      }
    }
    return true;
  }

  public int depthAt(int offset) {
    int depth = 0;
    for (var brace : braces) {
      if (brace.offset() >= offset) {
        break;
      }
      depth = brace.opening() ? depth + 1 : Math.max(0, depth - 1);
    }
    return depth;
  }
}
