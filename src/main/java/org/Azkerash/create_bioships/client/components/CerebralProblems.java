package org.Azkerash.create_bioships.client.components;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.Azkerash.create_bioships.core.brain.program.editor.EditorProblem;

/** Independently scrollable current-problem list or retained build/error log. */
final class CerebralProblems extends AbstractScrollWidget {
  private record Row(EditorProblem problem, FormattedCharSequence text) {}

  private final Font font;
  private final Consumer<EditorProblem> navigate;
  private List<EditorProblem> entries = List.of();
  private List<Row> rows = List.of();
  private String fingerprint = "";
  private boolean log;

  CerebralProblems(
      Font font, int x, int y, int width, int height, Consumer<EditorProblem> navigate) {
    super(x, y, width, height, Component.literal("Program diagnostics"));
    this.font = font;
    this.navigate = navigate;
  }

  void entries(List<EditorProblem> value, String fingerprint, boolean log) {
    if (entries.equals(value) && this.fingerprint.equals(fingerprint) && this.log == log) {
      return;
    }
    this.entries = List.copyOf(value);
    this.fingerprint = fingerprint;
    this.log = log;
    var result = new ArrayList<Row>();
    for (var problem : entries) {
      String kind =
          switch (problem.severity()) {
            case ERROR -> "Error";
            case WARNING -> "Warning";
            case INFO -> "Info";
          };
      String older =
          !problem.fingerprint().isEmpty() && !problem.fingerprint().equals(fingerprint)
              ? " [older source]"
              : "";
      for (var text :
          font.split(
              Component.literal(kind + older + " · " + problem.label()),
              width - totalInnerPadding())) {
        result.add(new Row(problem, text));
      }
    }
    rows = List.copyOf(result);
    setScrollAmount(0);
  }

  EditorProblem hovered(double mx, double my) {
    if (!visible || !withinContentAreaPoint(mx, my)) {
      return null;
    }
    int index = (int) ((my - getY() - innerPadding() + scrollAmount()) / (font.lineHeight + 2));
    return index >= 0 && index < rows.size() ? rows.get(index).problem() : null;
  }

  @Override
  public boolean mouseClicked(double mx, double my, int button) {
    var problem = hovered(mx, my);
    if (button == 0 && problem != null) {
      if (problem.line() > 0 && problem.fingerprint().equals(fingerprint)) {
        navigate.accept(problem);
      }
      return true;
    }
    return super.mouseClicked(mx, my, button);
  }

  @Override
  protected int getInnerHeight() {
    return rows.size() * (font.lineHeight + 2);
  }

  @Override
  protected double scrollRate() {
    return (font.lineHeight + 2) * 3;
  }

  @Override
  protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
    if (rows.isEmpty()) {
      g.drawString(
          font,
          log ? "No build or runtime messages yet." : "No current errors or warnings.",
          getX() + innerPadding(),
          getY() + innerPadding(),
          OrganScreenStyle.MUTED,
          false);
    }
    int first = Math.max(0, (int) (scrollAmount() / (font.lineHeight + 2)) - 1),
        last = Math.min(rows.size(), first + height / (font.lineHeight + 2) + 4);
    for (int i = first; i < last; i++) {
      var row = rows.get(i);
      int color =
          switch (row.problem().severity()) {
            case ERROR -> 0xffef7583;
            case WARNING -> 0xff96c76e;
            case INFO -> OrganScreenStyle.MUTED;
          };
      g.drawString(
          font,
          row.text(),
          getX() + innerPadding(),
          getY() + innerPadding() + i * (font.lineHeight + 2),
          color,
          false);
    }
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(
        NarratedElementType.TITLE,
        Component.literal(
            (log ? "Error log, " : "Current problems, ") + entries.size() + " entries"));
  }
}
