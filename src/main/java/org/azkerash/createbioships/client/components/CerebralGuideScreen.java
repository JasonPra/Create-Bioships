package org.azkerash.createbioships.client.components;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.azkerash.createbioships.core.brain.program.CerebralGuide;

/** A chapter-indexed field manual; documentation is reloaded from resources whenever opened. */
public final class CerebralGuideScreen extends Screen {
  private static final int CHAPTER_ROW_HEIGHT = 20;
  private final List<CerebralGuide.Chapter> chapters;
  private int chapter, left, top, panelWidth, panelHeight;
  private Contents contents;
  private Button copy;

  private CerebralGuideScreen(List<CerebralGuide.Chapter> chapters, Component title) {
    super(title);
    this.chapters = List.copyOf(chapters);
  }

  public static void open() {
    open(CerebralGuide.RESOURCE, Component.translatable("item.create_bioships.cerebral_codex"));
  }

  public static void open(String resource, Component title) {
    var mc = Minecraft.getInstance();
    List<CerebralGuide.Chapter> chapters;
    try (var reader =
        mc.getResourceManager()
            .openAsReader(ResourceLocation.fromNamespaceAndPath("create_bioships", resource))) {
      chapters = CerebralGuide.read(reader);
    } catch (Exception e) {
      LogUtils.getLogger().warn("Could not load field guide", e);
      chapters =
          List.of(
              new CerebralGuide.Chapter(
                  "Documentation unavailable",
                  "The guide resource could not be loaded. Check your resource packs and"
                      + " game log.",
                  ""));
    }
    if (resource.equals(org.azkerash.createbioships.equipment.BioshipGuideItem.RESOURCE)
        && mc.level != null) {
      var complete = new ArrayList<>(chapters);
      complete.addAll(
          org.azkerash.createbioships.guide.CraftingGuide.chapters(
              mc.level.getRecipeManager(), mc.level.registryAccess()));
      chapters = List.copyOf(complete);
    }
    mc.setScreen(new CerebralGuideScreen(chapters, title));
  }

  @Override
  protected void init() {
    panelWidth = Math.min(680, width - 20);
    panelHeight = height - 24;
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    int indexWidth = Math.min(140, panelWidth / 3);
    addRenderableWidget(new Index(left + 12, top + 32, indexWidth, panelHeight - 78));
    contents =
        addRenderableWidget(
            new Contents(
                left + indexWidth + 20, top + 32, panelWidth - indexWidth - 32, panelHeight - 78));
    copy =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Copy example"),
                    b -> {
                      minecraft.keyboardHandler.setClipboard(chapters.get(chapter).example());
                      b.setMessage(Component.literal("Copied!"));
                    })
                .bounds(left + 12, top + panelHeight - 32, 100, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(left + panelWidth - 72, top + panelHeight - 32, 60, 20)
            .build());
    select(chapter);
  }

  private void select(int index) {
    chapter = index;
    contents.reflow();
    copy.active = !chapters.get(chapter).example().isEmpty();
    copy.setMessage(Component.literal("Copy example"));
  }

  // Screen.render invokes this before rendering widgets. Drawing the frame here avoids blurring
  // the title and frame with a second background pass.
  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
    super.renderBackground(g, mx, my, partial);
    OrganScreenStyle.panel(g, left, top, panelWidth, panelHeight);
    g.drawString(font, title, left + 12, top + 10, OrganScreenStyle.TEXT, false);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private final class Index extends AbstractScrollWidget {
    Index(int x, int y, int width, int height) {
      super(x, y, width, height, Component.literal("Guide chapters"));
    }

    protected int getInnerHeight() {
      return chapters.size() * CHAPTER_ROW_HEIGHT;
    }

    protected double scrollRate() {
      return CHAPTER_ROW_HEIGHT;
    }

    protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
      for (int i = 0; i < chapters.size(); i++) {
        int y = getY() + innerPadding() + i * CHAPTER_ROW_HEIGHT;
        if (i == chapter) {
          g.fill(getX() + 1, y - 2, getX() + width - 1, y + 15, 0xff593049);
        }
        var label = Component.literal((i + 1) + ". " + chapters.get(i).title());
        g.drawWordWrap(
            font,
            label,
            getX() + innerPadding(),
            y,
            width - totalInnerPadding(),
            i == chapter ? OrganScreenStyle.TEXT : OrganScreenStyle.MUTED);
      }
    }

    public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0 && withinContentAreaPoint(mx, my)) {
        int index = (int) ((my - getY() - innerPadding() + scrollAmount()) / CHAPTER_ROW_HEIGHT);
        if (index >= 0 && index < chapters.size()) {
          select(index);
          return true;
        }
      }
      return super.mouseClicked(mx, my, button);
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, Component.literal("Select a guide chapter"));
    }
  }

  private final class Contents extends AbstractScrollWidget {
    private List<FormattedCharSequence> lines = List.of();

    Contents(int x, int y, int width, int height) {
      super(x, y, width, height, Component.literal("Guide text"));
    }

    void reflow() {
      var selected = chapters.get(chapter);
      var lines = new ArrayList<FormattedCharSequence>();
      int available = width - totalInnerPadding();
      lines.addAll(
          font.split(
              Component.literal(selected.title()).withStyle(ChatFormatting.BOLD), available));
      lines.add(FormattedCharSequence.EMPTY);
      lines.addAll(font.split(Component.literal(selected.body()), available));
      if (!selected.example().isEmpty()) {
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(
            font.split(
                Component.literal("Example — Copy example copies this text:")
                    .withStyle(ChatFormatting.ITALIC),
                available));
        lines.add(FormattedCharSequence.EMPTY);
        // Split each source line separately to keep indentation and blank lines visible.
        for (String line : selected.example().split("\n", -1)) {
          if (line.isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
          } else {
            lines.addAll(
                font.split(Component.literal(line).withStyle(ChatFormatting.AQUA), available));
          }
        }
      }
      this.lines = List.copyOf(lines);
      setScrollAmount(0);
    }

    protected int getInnerHeight() {
      return lines.size() * (font.lineHeight + 3);
    }

    protected double scrollRate() {
      return (font.lineHeight + 3) * 3;
    }

    protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
      int y = getY() + innerPadding();
      for (var line : lines) {
        g.drawString(font, line, getX() + innerPadding(), y, OrganScreenStyle.TEXT, false);
        y += font.lineHeight + 3;
      }
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, Component.literal(chapters.get(chapter).title()));
      output.add(NarratedElementType.HINT, Component.literal(chapters.get(chapter).body()));
    }
  }
}
