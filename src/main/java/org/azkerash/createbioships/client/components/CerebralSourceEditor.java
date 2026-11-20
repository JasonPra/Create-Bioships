package org.azkerash.createbioships.client.components;

import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.azkerash.createbioships.core.brain.program.editor.*;
import org.lwjgl.glfw.GLFW;

/**
 * Native source editor with original line numbers, lexical colors, folds and diagnostic squiggles.
 */
public final class CerebralSourceEditor extends AbstractScrollWidget {
  private static final int ROW_GAP_PIXELS = 3;
  private static final int MIN_GUTTER_PIXELS = 34;
  private static final int HORIZONTAL_SCROLL_STEP_PIXELS = 18;
  private static final long CARET_BLINK_PERIOD_MILLIS = 500;

  private record Part(int start, int end, SourceSyntax.Kind kind) {}

  private final Font font;
  private final SourceDocument document;
  private List<EditorProblem> problems = List.of();
  private String cachedText;
  private List<List<Part>> parts = List.of();
  private int horizontal;
  private boolean selecting;
  private SourceCompletion.Matches completion;
  private int completionIndex, completionCaret;
  private static final int MAX_COMPLETION_ROWS = 6;
  private int completionX, completionY, completionWidth, completionFirst, completionRows;

  public CerebralSourceEditor(
      Font font, int x, int y, int width, int height, SourceDocument document) {
    super(x, y, width, height, Component.literal("C# source editor"));
    this.font = font;
    this.document = document;
  }

  public SourceDocument document() {
    return document;
  }

  public String getValue() {
    return document.text();
  }

  public void setValue(String value) {
    document.setValue(value);
    ensureCaret();
  }

  public void problems(List<EditorProblem> problems) {
    this.problems = List.copyOf(problems);
  }

  public void goTo(int line, int column) {
    document.move(document.offset(Math.max(0, line - 1), Math.max(0, column - 1)), false);
    ensureCaret();
  }

  private int rowHeight() {
    return font.lineHeight + ROW_GAP_PIXELS;
  }

  private int gutter() {
    return Math.max(MIN_GUTTER_PIXELS, font.width(Integer.toString(document.lineCount())) + 18);
  }

  private int textX() {
    return getX() + gutter() + 4;
  }

  private int measure(String value) {
    return font.width(value.replace("\t", "    "));
  }

  private int xAt(int line, int column) {
    return textX()
        - horizontal
        + measure(document.line(line).substring(0, Math.min(column, document.line(line).length())));
  }

  @Override
  protected int getInnerHeight() {
    return document.visibleLines().size() * rowHeight();
  }

  @Override
  protected double scrollRate() {
    return rowHeight() * 3;
  }

  void reflow() {
    setScrollAmount(scrollAmount());
    ensureCaret();
  }

  private void refreshCompletions(boolean explicit) {
    completion = SourceCompletion.suggest(document, explicit);
    completionIndex = 0;
    completionCaret = document.caret();
  }

  boolean handleCompletionKey(int key) {
    if (!visible || !isFocused()) {
      return false;
    }
    if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_SPACE) {
      refreshCompletions(true);
      return true;
    }
    if (!hasCompletions() || Screen.hasControlDown() || Screen.hasShiftDown()) {
      return false;
    }
    switch (key) {
      case GLFW.GLFW_KEY_UP ->
          completionIndex = Math.floorMod(completionIndex - 1, completion.suggestions().size());
      case GLFW.GLFW_KEY_DOWN ->
          completionIndex = (completionIndex + 1) % completion.suggestions().size();
      case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> acceptCompletion();
      case GLFW.GLFW_KEY_ESCAPE -> completion = null;
      default -> {
        return false;
      }
    }
    return true;
  }

  private boolean hasCompletions() {
    return completion != null
        && !completion.suggestions().isEmpty()
        && completionCaret == document.caret()
        && visible
        && isFocused();
  }

  private void acceptCompletion() {
    SourceCompletion.accept(document, completion, completionIndex);
    completion = null;
    ensureCaret();
  }

  /** Draw outside the editor scissor so the suggestion list remains usable near its bottom edge. */
  void renderCompletions(GuiGraphics g) {
    if (!hasCompletions()) {
      return;
    }
    positionCompletions();
    g.pose().pushPose();
    g.pose().translate(0, 0, 300);
    g.fill(
        completionX - 1,
        completionY - 1,
        completionX + completionWidth + 1,
        completionY + completionRows * rowHeight() + 1,
        0xffb78b9d);
    g.fill(
        completionX,
        completionY,
        completionX + completionWidth,
        completionY + completionRows * rowHeight(),
        0xff201923);
    for (int row = 0; row < completionRows; row++) {
      int index = completionFirst + row;
      int y = completionY + row * rowHeight();
      if (index == completionIndex) {
        g.fill(completionX, y, completionX + completionWidth, y + rowHeight(), 0xff65435c);
      }
      g.drawString(
          font,
          font.plainSubstrByWidth(completion.suggestions().get(index).label(), completionWidth - 8),
          completionX + 4,
          y + 2,
          0xffead8c9,
          false);
    }
    g.pose().popPose();
  }

  private void positionCompletions() {
    int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
    int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
    completionRows = Math.min(MAX_COMPLETION_ROWS, completion.suggestions().size());
    completionWidth =
        Math.min(
            screenWidth - 8,
            Math.max(
                120,
                completion.suggestions().stream()
                    .mapToInt(value -> font.width(value.label()) + 8)
                    .max()
                    .orElse(120)));
    completionFirst =
        Math.max(
            0,
            Math.min(
                completionIndex - completionRows + 1,
                completion.suggestions().size() - completionRows));
    completionX =
        Math.max(
            4,
            Math.min(
                xAt(document.lineAt(document.caret()), document.column()),
                screenWidth - completionWidth - 4));
    int caretY =
        getY()
            + innerPadding()
            + document.visibleRow(document.lineAt(document.caret())) * rowHeight()
            - (int) scrollAmount();
    completionY = caretY + rowHeight();
    if (completionY + completionRows * rowHeight() > screenHeight - 4) {
      completionY = Math.max(4, caretY - completionRows * rowHeight());
    }
  }

  boolean clickCompletion(double mx, double my, int button) {
    if (!hasCompletions() || button != 0) {
      return false;
    }
    positionCompletions();
    if (mx >= completionX
        && mx < completionX + completionWidth
        && my >= completionY
        && my < completionY + completionRows * rowHeight()) {
      completionIndex = completionFirst + (int) (my - completionY) / rowHeight();
      acceptCompletion();
      return true;
    }
    completion = null;
    return false;
  }

  private void ensureCaret() {
    int line = document.lineAt(document.caret()),
        row = document.visibleRow(line),
        y = row * rowHeight();
    if (y < scrollAmount()) {
      setScrollAmount(y);
    } else if (y + rowHeight() > scrollAmount() + height - totalInnerPadding()) {
      setScrollAmount(y + rowHeight() - height + totalInnerPadding());
    }
    int x = measure(document.line(line).substring(0, document.column()));
    int available = Math.max(12, width - gutter() - 12);
    if (x < horizontal) {
      horizontal = x;
    } else if (x > horizontal + available) {
      horizontal = x - available;
    }
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (!isFocused() || !visible) {
      return false;
    }
    if (handleCompletionKey(key)) {
      return true;
    }
    completion = null;
    boolean shift = Screen.hasShiftDown(), control = Screen.hasControlDown();
    if (control) {
      switch (key) {
        case GLFW.GLFW_KEY_A -> document.selectAll();
        case GLFW.GLFW_KEY_C ->
            Minecraft.getInstance().keyboardHandler.setClipboard(document.selected());
        case GLFW.GLFW_KEY_X -> {
          Minecraft.getInstance().keyboardHandler.setClipboard(document.selected());
          if (document.caret() != document.anchor()) {
            document.insert("");
          }
        }
        case GLFW.GLFW_KEY_V ->
            document.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
        case GLFW.GLFW_KEY_Z -> {
          if (shift) {
            document.redo();
          } else {
            document.undo();
          }
        }
        case GLFW.GLFW_KEY_Y -> document.redo();
        case GLFW.GLFW_KEY_HOME -> document.move(0, shift);
        case GLFW.GLFW_KEY_END -> document.move(getValue().length(), shift);
        case GLFW.GLFW_KEY_LEFT -> document.moveWord(-1, shift);
        case GLFW.GLFW_KEY_RIGHT -> document.moveWord(1, shift);
        default -> {
          return false;
        }
      }
      ensureCaret();
      return true;
    }
    switch (key) {
      case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> document.enter();
      case GLFW.GLFW_KEY_TAB -> document.indent(shift);
      case GLFW.GLFW_KEY_BACKSPACE -> document.backspace();
      case GLFW.GLFW_KEY_DELETE -> document.delete();
      case GLFW.GLFW_KEY_LEFT -> document.moveCharacter(-1, shift);
      case GLFW.GLFW_KEY_RIGHT -> document.moveCharacter(1, shift);
      case GLFW.GLFW_KEY_HOME ->
          document.move(document.lineStart(document.lineAt(document.caret())), shift);
      case GLFW.GLFW_KEY_END ->
          document.move(document.lineEnd(document.lineAt(document.caret())), shift);
      case GLFW.GLFW_KEY_UP -> vertical(-1, shift);
      case GLFW.GLFW_KEY_DOWN -> vertical(1, shift);
      case GLFW.GLFW_KEY_PAGE_UP -> vertical(-Math.max(1, height / rowHeight() - 1), shift);
      case GLFW.GLFW_KEY_PAGE_DOWN -> vertical(Math.max(1, height / rowHeight() - 1), shift);
      default -> {
        return false;
      }
    }
    ensureCaret();
    if (key == GLFW.GLFW_KEY_BACKSPACE) {
      refreshCompletions(false);
    }
    return true;
  }

  private void vertical(int delta, boolean select) {
    int row = document.visibleRow(document.lineAt(document.caret()));
    row = Math.max(0, Math.min(document.visibleLines().size() - 1, row + delta));
    document.move(document.offset(document.visibleLines().get(row), document.column()), select);
  }

  @Override
  public boolean charTyped(char c, int modifiers) {
    if (!visible || !isFocused() || !StringUtil.isAllowedChatCharacter(c)) {
      return false;
    }
    document.type(c);
    ensureCaret();
    refreshCompletions(false);
    return true;
  }

  @Override
  public boolean mouseClicked(double mx, double my, int button) {
    if (!visible || button != 0 || !withinContentAreaPoint(mx, my)) {
      return super.mouseClicked(mx, my, button);
    }
    int line = mouseLine(my);
    if (mx < textX() && mx >= getX() + gutter() - 9 && document.foldable(line)) {
      document.toggleFold(line);
      setScrollAmount(scrollAmount());
      return true;
    }
    document.move(mouseOffset(line, mx), Screen.hasShiftDown());
    selecting = true;
    ensureCaret();
    return true;
  }

  @Override
  public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    if (selecting && button == 0 && visible) {
      document.move(mouseOffset(mouseLine(my), mx), true);
      ensureCaret();
      return true;
    }
    return super.mouseDragged(mx, my, button, dx, dy);
  }

  @Override
  public boolean mouseReleased(double mx, double my, int button) {
    selecting = false;
    return super.mouseReleased(mx, my, button);
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double dx, double dy) {
    if (!visible) {
      return false;
    }
    if (Screen.hasShiftDown() || dx != 0) {
      horizontal =
          Math.max(0, horizontal - (int) ((dx != 0 ? dx : dy) * HORIZONTAL_SCROLL_STEP_PIXELS));
      return true;
    }
    return super.mouseScrolled(mx, my, dx, dy);
  }

  private int mouseLine(double my) {
    int row = (int) Math.floor((my - getY() - innerPadding() + scrollAmount()) / rowHeight());
    return document
        .visibleLines()
        .get(Math.max(0, Math.min(document.visibleLines().size() - 1, row)));
  }

  private int mouseOffset(int line, double mx) {
    double point = mx - textX() + horizontal;
    String content = document.line(line);
    int x = 0;
    for (int i = 0; i < content.length(); ) {
      int next = i + Character.charCount(content.codePointAt(i));
      int width = measure(content.substring(i, next));
      if (point < x + width / 2.0) {
        return document.lineStart(line) + i;
      }
      x += width;
      i = next;
    }
    return document.lineEnd(line);
  }

  public EditorProblem hoveredProblem(double mx, double my) {
    if (!visible || !withinContentAreaPoint(mx, my) || mx < textX()) {
      return null;
    }
    int line = mouseLine(my), offset = mouseOffset(line, mx) - document.lineStart(line);
    for (var problem : problems) {
      if (problem.line() == line + 1
          && offset >= problem.column() - 1
          && offset < problem.column() - 1 + problem.length()) {
        return problem;
      }
    }
    return null;
  }

  @Override
  protected void renderBackground(GuiGraphics g) {
    super.renderBackground(g);
    g.fill(getX() + 1, getY() + 1, getX() + gutter(), getY() + height - 1, 0xff241923);
  }

  private void refreshParts() {
    if (getValue().equals(cachedText)) {
      return;
    }
    cachedText = getValue();
    var result = new ArrayList<List<Part>>();
    for (int i = 0; i < document.lineCount(); i++) {
      result.add(new ArrayList<>());
    }
    for (var span : document.syntax().spans()) {
      int first = document.lineAt(span.start()), last = document.lineAt(span.end());
      for (int line = first; line <= last; line++) {
        int start = Math.max(span.start(), document.lineStart(line)),
            end = Math.min(span.end(), document.lineEnd(line));
        if (end > start) {
          result.get(line).add(new Part(start, end, span.kind()));
        }
      }
    }
    parts = List.copyOf(result);
  }

  @Override
  protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
    refreshParts();
    int first = Math.max(0, (int) (scrollAmount() / rowHeight()) - 1);
    int last = Math.min(document.visibleLines().size(), first + height / rowHeight() + 4);
    for (int row = first; row < last; row++) {
      int line = document.visibleLines().get(row), y = getY() + innerPadding() + row * rowHeight();
      boolean error =
          problems.stream()
              .anyMatch(p -> p.line() == line + 1 && p.severity() == EditorProblem.Severity.ERROR);
      boolean warning =
          problems.stream()
              .anyMatch(
                  p -> p.line() == line + 1 && p.severity() == EditorProblem.Severity.WARNING);
      if (error || warning) {
        g.fill(getX() + 3, y + 2, getX() + 5, y + 6, error ? 0xffee7180 : 0xff9bc878);
      }
      String number = Integer.toString(line + 1);
      g.drawString(font, number, getX() + gutter() - 12 - font.width(number), y, 0xff967d8c, false);
      if (document.foldable(line)) {
        g.drawString(
            font,
            document.collapsed(line) ? "+" : "-",
            getX() + gutter() - 8,
            y,
            0xffdcc5c0,
            false);
      }
      g.enableScissor(textX(), getY() + 1, getX() + width - 1, getY() + height - 1);
      if (document.lineAt(document.caret()) == line) {
        g.fill(textX(), y - 1, getX() + width - 1, y + rowHeight() - 1, 0x203e334a);
      }
      int selectionStart = Math.max(document.selectionStart(), document.lineStart(line));
      int selectionEnd = Math.min(document.selectionEnd(), document.lineEnd(line));
      if (selectionEnd > selectionStart) {
        g.fill(
            xAt(line, selectionStart - document.lineStart(line)),
            y - 1,
            xAt(line, selectionEnd - document.lineStart(line)),
            y + rowHeight() - 1,
            0x995f426d);
      }
      int x = textX() - horizontal, offset = document.lineStart(line);
      for (var part : parts.get(line)) {
        if (part.start() > offset) {
          x += measure(getValue().substring(offset, part.start()));
        }
        String value = getValue().substring(part.start(), part.end()).replace("\t", "    ");
        int width = font.width(value);
        if (x + width >= textX() && x < getX() + this.width) {
          g.drawString(font, value, x, y, color(part.kind()), false);
        }
        x += width;
        offset = part.end();
      }
      if (document.collapsed(line)) {
        g.drawString(font, " …", x, y, 0xffb78b9d, false);
      }
      for (var problem : problems) {
        if (problem.line() == line + 1) {
          int start = xAt(line, Math.max(0, problem.column() - 1)),
              end = xAt(line, Math.max(0, problem.column() - 1) + problem.length());
          end = Math.max(start + 3, end);
          start = Math.max(textX(), start);
          end = Math.min(getX() + width - 2, end);
          int color = problem.severity() == EditorProblem.Severity.ERROR ? 0xffef7583 : 0xff96c76e;
          for (int at = start; at < end; at++) {
            g.fill(
                at,
                y + font.lineHeight + (at - start) % 2,
                at + 1,
                y + font.lineHeight + (at - start) % 2 + 1,
                color);
          }
        }
      }
      if (isFocused()
          && document.lineAt(document.caret()) == line
          && Util.getMillis() / CARET_BLINK_PERIOD_MILLIS % 2 == 0) {
        int caret = xAt(line, document.column());
        g.fill(caret, y - 1, caret + 1, y + font.lineHeight + 1, 0xfff5e5d2);
      }
      g.disableScissor();
    }
  }

  private static int color(SourceSyntax.Kind kind) {
    return switch (kind) {
      case KEYWORD -> 0xffcc99d4;
      case TYPE -> 0xff86bfe0;
      case METHOD -> 0xffe1cd87;
      case PROPERTY, NUMBER -> 0xff94d0c1;
      case STRING -> 0xffcbb090;
      case COMMENT -> 0xff879d79;
      default -> 0xffead8c9;
    };
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(
        NarratedElementType.TITLE,
        Component.literal(
            "C# source, line "
                + (document.lineAt(document.caret()) + 1)
                + ", column "
                + (document.column() + 1)));
  }
}
