package org.azkerash.createbioships.client.components;

import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Read-only, wrapped, scrollable private runtime output. */
final class ProgramConsole extends AbstractScrollWidget {
  private final Font font;
  private List<String> lines = List.of();
  private List<FormattedCharSequence> wrapped = List.of();

  ProgramConsole(Font font, int x, int y, int width, int height) {
    super(x, y, width, height, Component.literal("Private program console"));
    this.font = font;
  }

  void lines(List<String> value) {
    if (lines.equals(value)) {
      return;
    }
    boolean tail = scrollAmount() >= getMaxScrollAmount() - 1;
    lines = List.copyOf(value);
    var next = new ArrayList<FormattedCharSequence>();
    for (String line : lines) {
      next.addAll(font.split(Component.literal(line), width - totalInnerPadding()));
    }
    wrapped = List.copyOf(next);
    if (tail) {
      setScrollAmount(getMaxScrollAmount());
    }
  }

  protected int getInnerHeight() {
    return wrapped.size() * font.lineHeight;
  }

  protected double scrollRate() {
    return font.lineHeight;
  }

  protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
    int y = getY() + innerPadding();
    if (wrapped.isEmpty()) {
      g.drawString(
          font,
          "No console output yet.",
          getX() + innerPadding(),
          y,
          OrganScreenStyle.MUTED,
          false);
    }
    for (var line : wrapped) {
      g.drawString(font, line, getX() + innerPadding(), y, OrganScreenStyle.TEXT, false);
      y += font.lineHeight;
    }
  }

  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(
        NarratedElementType.TITLE,
        Component.literal("Private console, " + lines.size() + " lines"));
  }
}
