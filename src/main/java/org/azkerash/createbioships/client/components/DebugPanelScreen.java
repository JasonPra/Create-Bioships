package org.azkerash.createbioships.client.components;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.components.logic.diagnostics.DebugPanelPayload;
import org.azkerash.createbioships.equipment.LocomotionGuideItem;

/** Nerve-style read-only inspection; only an open screen requests periodic server snapshots. */
public final class DebugPanelScreen extends Screen {
  private static final String[] TABS = {
    "Overview", "Legs", "Components", "Navigation", "Collisions", "Advice"
  };
  private static final int REFRESH_TICKS = 20;
  private DebugPanelPayload data;
  private int tab, x, y, panelWidth, panelHeight;
  private long nextPoll;
  private Contents contents;

  private DebugPanelScreen(DebugPanelPayload data) {
    super(Component.literal("Debugging Panel"));
    this.data = data;
  }

  public static void receive(DebugPanelPayload data) {
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof DebugPanelScreen screen
        && screen.data.grid().equals(data.grid())
        && screen.data.panel().equals(data.panel())) {
      screen.data = data;
      if (screen.contents != null) {
        screen.contents.reflow(false);
      }
    } else if (data.opening()) {
      mc.setScreen(new DebugPanelScreen(data));
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  protected void init() {
    panelWidth = Math.min(680, width - 16);
    panelHeight = height - 20;
    x = (width - panelWidth) / 2;
    y = (height - panelHeight) / 2;
    int tabWidth = (panelWidth - 24) / TABS.length;
    for (int i = 0; i < TABS.length; i++) {
      final int index = i;
      addRenderableWidget(
          new OrganButton(
                  x + 12 + i * tabWidth,
                  y + 49,
                  tabWidth - 3,
                  18,
                  TABS[i],
                  b -> {
                    tab = index;
                    contents.reflow(true);
                  })
              .selected(() -> tab == index)
              .accent(0xffa8c67d));
    }
    contents =
        addRenderableWidget(new Contents(x + 12, y + 74, panelWidth - 24, panelHeight - 113));
    contents.reflow(false);
    addRenderableWidget(
        new OrganButton(x + 12, y + panelHeight - 29, 80, 18, "Refresh", b -> poll()));
    addRenderableWidget(
        new OrganButton(
            x + 100,
            y + panelHeight - 29,
            110,
            18,
            "Field guide",
            b ->
                CerebralGuideScreen.open(
                    LocomotionGuideItem.RESOURCE,
                    Component.translatable("item.create_bioships.locomotion_codex"))));
    addRenderableWidget(
        new OrganButton(
            x + panelWidth - 92, y + panelHeight - 29, 80, 18, "Close", b -> onClose()));
  }

  private void poll() {
    if (minecraft.level != null && minecraft.level.getGameTime() >= nextPoll) {
      nextPoll = minecraft.level.getGameTime() + REFRESH_TICKS;
      PacketDistributor.sendToServer(new DebugPanelPayload.Request(data.grid(), data.panel()));
    }
  }

  @Override
  public void tick() {
    poll();
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    renderBackground(g, mx, my, partial);
    OrganScreenStyle.panel(g, x, y, panelWidth, panelHeight);
    // Bug glyph and probe brackets distinguish this terminal from permission management.
    int green = 0xffafd88d;
    g.fill(x + 20, y + 14, x + 28, y + 33, green);
    g.fill(x + 22, y + 10, x + 26, y + 14, green);
    for (int row = 0; row < 3; row++) {
      g.fill(x + 15, y + 16 + row * 6, x + 20, y + 18 + row * 6, green);
      g.fill(x + 28, y + 16 + row * 6, x + 33, y + 18 + row * 6, green);
    }
    g.fill(x + 23, y + 17, x + 25, y + 25, 0xff27251c);
    g.fill(x + 23, y + 28, x + 25, y + 30, 0xff27251c);
    g.drawString(font, title, x + 43, y + 13, OrganScreenStyle.TEXT, false);
    long age =
        minecraft.level == null ? 0 : Math.max(0, minecraft.level.getGameTime() - data.tick());
    g.drawString(
        font,
        "Read-only · sample "
            + data.tick()
            + (age > 60 ? " · STALE: move near panel / refresh" : " · live refresh"),
        x + 43,
        y + 29,
        age > 60 ? 0xffe4b378 : OrganScreenStyle.MUTED,
        false);
    OrganScreenStyle.divider(g, x + 12, y + 44, panelWidth - 24);
    super.render(g, mx, my, partial);
  }

  private final class Contents extends AbstractScrollWidget {
    private record Line(FormattedCharSequence text, int color) {}

    private List<Line> lines = List.of();

    Contents(int x, int y, int w, int h) {
      super(x, y, w, h, Component.literal("Creature diagnostics"));
    }

    void append(List<Line> lines, String text, int color) {
      font.split(Component.literal(text), width - totalInnerPadding())
          .forEach(line -> lines.add(new Line(line, color)));
    }

    void reflow(boolean reset) {
      var next = new ArrayList<Line>();
      for (var row : data.rows()) {
        if (!row.section().equals(TABS[tab])) {
          continue;
        }
        append(
            next,
            row.title(),
            row.severity() == 2 ? 0xffed9991 : row.severity() == 1 ? 0xffe4c187 : 0xffb4d39a);
        append(next, row.detail(), OrganScreenStyle.TEXT);
        if (!row.suggestion().isBlank()) {
          append(next, "Suggestion: " + row.suggestion(), OrganScreenStyle.MUTED);
        }
        next.add(new Line(FormattedCharSequence.EMPTY, 0));
      }
      if (next.isEmpty()) {
        append(next, "No entries in this category.", OrganScreenStyle.MUTED);
      }
      lines = List.copyOf(next);
      setScrollAmount(reset ? 0 : Math.min(scrollAmount(), Math.max(0, getInnerHeight() - height)));
    }

    protected int getInnerHeight() {
      return lines.size() * (font.lineHeight + 3);
    }

    protected double scrollRate() {
      return (font.lineHeight + 3) * 3;
    }

    protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
      int at = getY() + innerPadding();
      for (var line : lines) {
        g.drawString(font, line.text(), getX() + innerPadding(), at, line.color(), false);
        at += font.lineHeight + 3;
      }
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(
          NarratedElementType.TITLE,
          Component.literal("Read-only diagnostics and suggested fixes"));
    }
  }
}
