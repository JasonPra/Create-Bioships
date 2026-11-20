package org.Azkerash.create_bioships.core.brain.program.editor;

import java.util.*;
import java.util.function.Consumer;
import org.Azkerash.create_bioships.core.brain.program.ProgramLimits;

/** Source, selection, history and fold projection. Folding never changes the program text. */
public final class SourceDocument {
  public static final int INDENT = 4;

  private record Snapshot(String text, int caret, int anchor, Set<Integer> collapsed) {}

  private String text;
  private int caret, anchor;
  private int[] starts;
  private SourceSyntax syntax;
  private final Map<Integer, Integer> folds = new TreeMap<>();
  private final Set<Integer> collapsed = new HashSet<>();
  private List<Integer> visible;
  private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
  private Consumer<String> listener = value -> {};

  public SourceDocument(String source) {
    text = bounded(source);
    caret = anchor = text.length();
    rebuild();
  }

  public String text() {
    return text;
  }

  public int caret() {
    return caret;
  }

  public int anchor() {
    return anchor;
  }

  public int selectionStart() {
    return Math.min(caret, anchor);
  }

  public int selectionEnd() {
    return Math.max(caret, anchor);
  }

  public String selected() {
    return text.substring(selectionStart(), selectionEnd());
  }

  public int lineCount() {
    return starts.length;
  }

  public int lineStart(int line) {
    return starts[clamp(line, 0, starts.length - 1)];
  }

  public int lineEnd(int line) {
    line = clamp(line, 0, starts.length - 1);
    return line + 1 < starts.length ? starts[line + 1] - 1 : text.length();
  }

  public String line(int line) {
    return text.substring(lineStart(line), lineEnd(line));
  }

  public int lineAt(int offset) {
    int index = Arrays.binarySearch(starts, clamp(offset, 0, text.length()));
    return index >= 0 ? index : -index - 2;
  }

  public int column() {
    return caret - lineStart(lineAt(caret));
  }

  public int offset(int line, int column) {
    return Math.min(lineEnd(line), lineStart(line) + Math.max(0, column));
  }

  public SourceSyntax syntax() {
    return syntax;
  }

  public List<Integer> visibleLines() {
    return visible;
  }

  public int visibleRow(int line) {
    int row = Collections.binarySearch(visible, line);
    return row >= 0 ? row : Math.max(0, -row - 2);
  }

  public boolean foldable(int line) {
    return folds.containsKey(line);
  }

  public boolean collapsed(int line) {
    return collapsed.contains(line);
  }

  public void listener(Consumer<String> listener) {
    this.listener = listener;
  }

  public void setValue(String value) {
    replace(0, text.length(), bounded(value));
  }

  public void insert(String value) {
    replace(selectionStart(), selectionEnd(), value.replace("\r\n", "\n").replace('\r', '\n'));
  }

  public void selectAll() {
    anchor = 0;
    caret = text.length();
    reveal(lineAt(caret));
  }

  public void move(int offset, boolean select) {
    caret = clamp(offset, 0, text.length());
    if (!select) anchor = caret;
    reveal(lineAt(caret));
  }

  public void moveCharacter(int direction, boolean select) {
    if (!select && caret != anchor) {
      move(direction < 0 ? selectionStart() : selectionEnd(), false);
      return;
    }
    if (direction < 0 && caret > 0) move(Character.offsetByCodePoints(text, caret, -1), select);
    else if (direction > 0 && caret < text.length())
      move(Character.offsetByCodePoints(text, caret, 1), select);
  }

  public void moveWord(int direction, boolean select) {
    int point = caret;
    if (direction < 0) {
      while (point > 0 && Character.isWhitespace(text.charAt(point - 1))) point--;
      while (point > 0 && word(text.charAt(point - 1))) point--;
      if (point == caret && point > 0) point--;
    } else {
      while (point < text.length() && word(text.charAt(point))) point++;
      while (point < text.length() && Character.isWhitespace(text.charAt(point))) point++;
      if (point == caret && point < text.length()) point++;
    }
    move(point, select);
  }

  public void backspace() {
    if (caret != anchor) {
      insert("");
      return;
    }
    if (caret == 0) return;
    int start = lineStart(lineAt(caret));
    String prefix = text.substring(start, caret);
    if (!prefix.isEmpty() && prefix.chars().allMatch(c -> c == ' ')) {
      int count = prefix.length() % INDENT;
      if (count == 0) count = INDENT;
      replace(caret - count, caret, "");
    } else replace(Character.offsetByCodePoints(text, caret, -1), caret, "");
  }

  public void delete() {
    if (caret != anchor) {
      insert("");
      return;
    }
    if (caret < text.length()) replace(caret, Character.offsetByCodePoints(text, caret, 1), "");
  }

  public void type(char c) {
    if (c == '{' && syntax.codeAt(selectionStart())) {
      int point = selectionStart();
      insert("{}");
      move(point + 1, false);
      return;
    }
    if (c == '}'
        && caret == anchor
        && caret < text.length()
        && text.charAt(caret) == '}'
        && syntax.codeAt(caret)) {
      move(caret + 1, false);
      return;
    }
    if (c == '}'
        && syntax.codeAt(selectionStart())
        && text.substring(lineStart(lineAt(selectionStart())), selectionStart()).isBlank()) {
      int point = selectionStart();
      int end = selectionEnd();
      int indent = Math.max(0, Math.min(64, syntax.depthAt(point)) - 1) * INDENT;
      replace(lineStart(lineAt(point)), end, " ".repeat(indent) + "}");
      return;
    }
    insert(String.valueOf(c));
  }

  public void enter() {
    int point = selectionStart(), end = selectionEnd();
    int depth = Math.min(64, syntax.depthAt(point));
    int indent = syntax.codeAt(point) ? depth * INDENT : leadingSpaces(line(lineAt(point)));
    int before = point - 1;
    while (before >= 0 && (text.charAt(before) == ' ' || text.charAt(before) == '\t')) before--;
    int after = end;
    while (after < text.length() && (text.charAt(after) == ' ' || text.charAt(after) == '\t'))
      after++;
    boolean pair =
        syntax.codeAt(point)
            && before >= 0
            && text.charAt(before) == '{'
            && after < text.length()
            && text.charAt(after) == '}';
    String insertion = "\n" + " ".repeat(indent);
    if (pair) insertion += "\n" + " ".repeat(Math.max(0, depth - 1) * INDENT);
    replace(point, pair ? after : end, insertion);
    if (pair) move(Math.min(text.length(), point + 1 + indent), false);
  }

  public void indent(boolean outdent) {
    if (caret == anchor && !outdent) {
      insert(" ".repeat(INDENT));
      return;
    }
    int first = lineAt(selectionStart()), last = lineAt(selectionEnd());
    if (last > first && selectionEnd() == lineStart(last)) last--;
    int start = lineStart(first), end = lineEnd(last);
    StringBuilder value = new StringBuilder();
    int firstShift = 0;
    for (int line = first; line <= last; line++) {
      String content = line(line);
      int removed = 0;
      if (outdent) {
        while (removed < Math.min(INDENT, content.length()) && content.charAt(removed) == ' ')
          removed++;
        if (removed == 0 && content.startsWith("\t")) removed = 1;
      }
      if (line == first) firstShift = outdent ? -removed : INDENT;
      value.append(outdent ? content.substring(removed) : " ".repeat(INDENT) + content);
      if (line < last) value.append('\n');
    }
    boolean selection = caret != anchor;
    int oldCaret = caret;
    replace(start, end, value.toString());
    if (selection) {
      anchor = start;
      caret = start + value.length();
    } else move(Math.max(start, oldCaret + firstShift), false);
  }

  public void toggleFold(int line) {
    Integer end = folds.get(line);
    if (end == null) return;
    if (!collapsed.remove(line)) {
      collapsed.add(line);
      if (lineAt(caret) > line && lineAt(caret) < end
          || lineAt(anchor) > line && lineAt(anchor) < end) caret = anchor = lineEnd(line);
    }
    project();
  }

  public void reveal(int line) {
    boolean changed =
        collapsed.removeIf(start -> start < line && line < folds.getOrDefault(start, start));
    if (changed) project();
  }

  public void undo() {
    restore(undo, redo);
  }

  public void redo() {
    restore(redo, undo);
  }

  private void restore(Deque<Snapshot> from, Deque<Snapshot> to) {
    if (from.isEmpty()) return;
    to.push(snapshot());
    Snapshot state = from.pop();
    text = state.text();
    caret = state.caret();
    anchor = state.anchor();
    collapsed.clear();
    collapsed.addAll(state.collapsed());
    rebuild();
    listener.accept(text);
  }

  private void replace(int start, int end, String value) {
    int capacity = ProgramLimits.SOURCE - (text.length() - (end - start));
    value = value.substring(0, Math.min(value.length(), capacity));
    String next = text.substring(0, start) + value + text.substring(end);
    if (next.equals(text)) {
      move(start + value.length(), false);
      return;
    }
    undo.push(snapshot());
    while (undo.size() > 64) undo.removeLast();
    redo.clear();
    text = next;
    caret = anchor = start + value.length();
    collapsed.clear();
    rebuild();
    listener.accept(text);
  }

  private Snapshot snapshot() {
    return new Snapshot(text, caret, anchor, Set.copyOf(collapsed));
  }

  private void rebuild() {
    starts =
        java.util.stream.IntStream.concat(
                java.util.stream.IntStream.of(0),
                java.util.stream.IntStream.range(0, text.length())
                    .filter(i -> text.charAt(i) == '\n')
                    .map(i -> i + 1))
            .toArray();
    syntax = new SourceSyntax(text);
    folds.clear();
    for (var block : syntax.blocks()) {
      int start = lineAt(block.start()), end = lineAt(block.end());
      if (end > start + 1) folds.merge(start, end, Math::max);
    }
    collapsed.retainAll(folds.keySet());
    project();
  }

  private void project() {
    var result = new ArrayList<Integer>();
    for (int line = 0; line < starts.length; line++) {
      result.add(line);
      if (collapsed.contains(line)) line = folds.get(line) - 1;
    }
    visible = List.copyOf(result);
  }

  private static String bounded(String value) {
    return value.substring(0, Math.min(value.length(), ProgramLimits.SOURCE));
  }

  private static boolean word(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  private static int leadingSpaces(String line) {
    int n = 0;
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == ' ') n++;
      else if (line.charAt(i) == '\t') n += INDENT;
      else break;
    }
    return Math.min(n, 256);
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
