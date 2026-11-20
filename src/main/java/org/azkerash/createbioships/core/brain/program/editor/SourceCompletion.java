package org.azkerash.createbioships.core.brain.program.editor;

import java.util.*;

/** Suggestions for the sandbox language only; this is not a full C# reflection service. */
public final class SourceCompletion {
  public record Suggestion(String label, String text, int caretBack) {}

  public record Matches(int start, int end, List<Suggestion> suggestions) {}

  private static final List<String> WORDS =
      List.of(
          "using",
          "class",
          "Program",
          "public",
          "static",
          "void",
          "Main",
          "int",
          "double",
          "bool",
          "string",
          "var",
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
          "Brain",
          "Console",
          "System");
  private static final Set<String> LOCAL_TYPES = Set.of("int", "double", "bool", "string", "var");

  private SourceCompletion() {}

  public static Matches suggest(SourceDocument document, boolean explicit) {
    String source = document.text();
    int caret = document.caret(), start = caret, end = caret;
    if (document.anchor() != caret || !document.syntax().codeAt(caret)) {
      return new Matches(caret, caret, List.of());
    }
    while (start > 0 && identifier(source.charAt(start - 1))) {
      start--;
    }
    while (end < source.length() && identifier(source.charAt(end))) {
      end++;
    }
    String prefix = source.substring(start, caret);
    var choices = new LinkedHashMap<String, Suggestion>();
    int previous = start;
    while (previous > 0 && Character.isWhitespace(source.charAt(previous - 1))) {
      previous--;
    }
    boolean member = previous > 0 && source.charAt(previous - 1) == '.';
    if (member) {
      int ownerEnd = previous - 1;
      while (ownerEnd > 0 && Character.isWhitespace(source.charAt(ownerEnd - 1))) {
        ownerEnd--;
      }
      int ownerStart = ownerEnd;
      while (ownerStart > 0 && identifier(source.charAt(ownerStart - 1))) {
        ownerStart--;
      }
      switch (source.substring(ownerStart, ownerEnd)) {
        case "Brain" -> {
          add(choices, new Suggestion("Wait(int ticks)", "Wait()", 1));
          for (String stat : List.of("Health", "Hunger", "Oxygen", "Tick")) {
            add(choices, new Suggestion(stat + " : double", stat, 0));
          }
        }
        case "Console" -> add(choices, new Suggestion("WriteLine(value)", "WriteLine()", 1));
        default -> {}
      }
    } else if (explicit || !prefix.isEmpty()) {
      for (String word : WORDS) {
        add(choices, new Suggestion(word, word, 0));
      }
      // Include declarations before the caret without suggesting identifiers from comments/strings.
      var spans = document.syntax().spans();
      for (int i = 1; i < spans.size(); i++) {
        var type = spans.get(i - 1);
        var name = spans.get(i);
        if (name.end() <= start
            && name.kind() == SourceSyntax.Kind.NAME
            && LOCAL_TYPES.contains(source.substring(type.start(), type.end()))) {
          String word = source.substring(name.start(), name.end());
          add(choices, new Suggestion(word, word, 0));
        }
      }
    }
    return new Matches(
        start,
        end,
        choices.values().stream()
            .filter(value -> value.text().startsWith(prefix) && !value.text().equals(prefix))
            .sorted(Comparator.comparing(Suggestion::text))
            .toList());
  }

  private static boolean identifier(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  private static void add(Map<String, Suggestion> choices, Suggestion suggestion) {
    choices.put(suggestion.text(), suggestion);
  }

  public static void accept(SourceDocument document, Matches matches, int index) {
    var choice = matches.suggestions().get(index);
    document.move(matches.start(), false);
    document.move(matches.end(), true);
    document.insert(choice.text());
    document.move(document.caret() - choice.caretBack(), false);
  }
}
