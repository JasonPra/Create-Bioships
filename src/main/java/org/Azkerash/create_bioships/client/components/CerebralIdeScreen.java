package org.Azkerash.create_bioships.client.components;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.components.logic.brain.*;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;
import org.Azkerash.create_bioships.core.brain.program.editor.*;

/** Source editing stays client-side; only explicit saves alter a physical card on the server. */
public final class CerebralIdeScreen extends AbstractContainerScreen<CardWorkspaceMenu> {
  private CerebralSourceEditor editor;
  private final SourceDocument document;
  private List<EditorProblem> problems = List.of();
  private int analysisDelay = -1;
  private ProgramConsole console;
  private boolean showingConsole;
  private int pollTicks;
  private String runtimeState = "Waiting for runtime status…";
  private java.util.List<String> output = java.util.List.of();
  private String status = "Draft editor · Save explicitly to write the card.";
  private boolean dirty;

  public CerebralIdeScreen(CardWorkspaceMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    document = new SourceDocument(menu.initialSource);
    document.listener(
        value -> {
          dirty = true;
          analysisDelay = 8;
          if (editor != null) editor.problems(List.of());
        });
    if (menu.initiallyCompiled) status = "Compiled card · Changed source must be compiled again.";
  }

  @Override
  protected void init() {
    imageWidth = Math.min(760, width - 20);
    imageHeight = height - 24;
    super.init();
    editor =
        addRenderableWidget(
            new CerebralSourceEditor(
                font, leftPos + 12, topPos + 39, imageWidth - 24, imageHeight - 110, document));
    analyzeNow();
    addRenderableWidget(
        Button.builder(Component.literal("Save draft"), b -> save())
            .bounds(leftPos + 12, topPos + imageHeight - 32, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(leftPos + imageWidth - 72, topPos + imageHeight - 32, 60, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Compile & write"), b -> compile())
            .bounds(leftPos + 118, topPos + imageHeight - 32, 106, 20)
            .build());
    console =
        addRenderableWidget(
            new ProgramConsole(
                font, leftPos + 12, topPos + 39, imageWidth - 24, imageHeight - 110));
    console.lines(output);
    console.visible = showingConsole;
    editor.visible = !showingConsole;
    addRenderableWidget(
        Button.builder(
                Component.literal("Source / console"),
                b -> {
                  showingConsole = !showingConsole;
                  console.visible = showingConsole;
                  editor.visible = !showingConsole;
                  setFocused(showingConsole ? console : editor);
                })
            .bounds(leftPos + imageWidth - 112, topPos + 25, 100, 12)
            .build());
    setInitialFocus(showingConsole ? console : editor);
  }

  private void analyzeNow() {
    analysisDelay = -1;
    problems =
        EditorAnalysis.analyze(document.text(), ProgramCardData.fingerprint(document.text()));
    editor.problems(problems);
  }

  private void save() {
    PacketDistributor.sendToServer(
        new CardWorkspaceNetwork.Save(menu.containerId, menu.identity, editor.getValue()));
    status = "Saving…";
  }

  private void compile() {
    analyzeNow();
    PacketDistributor.sendToServer(
        new CardWorkspaceNetwork.Write(menu.containerId, menu.identity, editor.getValue()));
    status = "Compiling on the server…";
  }

  public static void receive(CardWorkspaceNetwork.Result result) {
    if (Minecraft.getInstance().screen instanceof CerebralIdeScreen screen
        && screen.menu.containerId == result.menu()
        && screen.menu.identity.equals(result.identity())) {
      screen.status = result.message();
      if (result.saved()
          && result.fingerprint().equals(ProgramCardData.fingerprint(screen.editor.getValue())))
        screen.dirty = false;
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    if (analysisDelay > 0 && --analysisDelay == 0) analyzeNow();
    if (pollTicks++ % 20 == 0)
      PacketDistributor.sendToServer(
          new CardWorkspaceNetwork.Poll(menu.containerId, menu.identity));
  }

  public static void receiveOutput(CardWorkspaceNetwork.Output result) {
    if (Minecraft.getInstance().screen instanceof CerebralIdeScreen screen
        && screen.menu.containerId == result.menu()
        && screen.menu.identity.equals(result.identity())) {
      screen.runtimeState = result.state();
      screen.output = result.lines();
      screen.console.lines(result.lines());
    }
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
      compile();
      return true;
    }
    if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
      save();
      return true;
    }
    if (editor.isFocused() && minecraft.options.keyInventory.matches(key, scan))
      return editor.keyPressed(key, scan, modifiers);
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    OrganScreenStyle.panel(g, leftPos, topPos, imageWidth, imageHeight);
    g.drawString(
        font,
        font.plainSubstrByWidth(
            showingConsole
                ? "Private console"
                : "Source.cs"
                    + (dirty ? " *" : "")
                    + " · Ln "
                    + (document.lineAt(document.caret()) + 1)
                    + ", Col "
                    + (document.column() + 1),
            imageWidth - 132),
        leftPos + 12,
        topPos + 27,
        OrganScreenStyle.MUTED,
        false);
    g.fill(
        leftPos + 10,
        topPos + 37,
        leftPos + imageWidth - 10,
        topPos + imageHeight - 69,
        0xff100e16);
    g.drawString(
        font,
        font.plainSubstrByWidth(showingConsole ? runtimeState : status, imageWidth - 24),
        leftPos + 12,
        topPos + imageHeight - 61,
        OrganScreenStyle.TEXT,
        false);
    g.drawString(
        font,
        "Ctrl+S saves · Ctrl+Enter compiles",
        leftPos + 12,
        topPos + imageHeight - 48,
        OrganScreenStyle.MUTED,
        false);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 12, 10, OrganScreenStyle.TEXT, false);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    var hovered = editor.hoveredProblem(mx, my);
    if (hovered != null) g.renderTooltip(font, Component.literal(hovered.message()), mx, my);
    if (mx >= leftPos + 12
        && mx < leftPos + imageWidth - 12
        && my >= topPos + imageHeight - 63
        && my < topPos + imageHeight - 50)
      g.renderTooltip(font, Component.literal(showingConsole ? runtimeState : status), mx, my);
  }
}
