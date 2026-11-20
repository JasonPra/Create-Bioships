package org.azkerash.createbioships.client.components;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.components.logic.brain.*;
import org.azkerash.createbioships.core.brain.program.ProgramCardData;
import org.azkerash.createbioships.core.brain.program.editor.*;

/** Source editing stays client-side; only explicit saves alter a physical card on the server. */
public final class CerebralIdeScreen extends AbstractContainerScreen<CardWorkspaceMenu> {
  private static final int LIVE_ANALYSIS_DELAY_TICKS = 8;
  private static final int DIVIDER_HEIGHT = 8;
  private static final int DIAGNOSTIC_HEADER_HEIGHT = 18;
  private static final int MIN_SOURCE_HEIGHT = 36;
  private static final int MIN_DIAGNOSTIC_HEIGHT = 24;
  private final CerebralIdeLayout layout = CerebralIdeLayout.load();
  private Button diagnosticsToggle;
  private int dividerTop;
  private boolean resizingPanels;
  private static final int STATUS_POLL_INTERVAL_TICKS = 20;

  private CerebralSourceEditor editor;
  private final SourceDocument document;
  private final EditorLog log;
  private CerebralProblems tracker;
  private Button problemsTab, logTab, clearLog;
  private boolean showingLog;
  private String installedFingerprint;
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
    document = new SourceDocument(menu.getInitialSource());
    document.move(0, false);
    log = CerebralDiagnosticLogs.get(menu.getIdentity());
    installedFingerprint =
        menu.isInitiallyCompiled() ? ProgramCardData.fingerprint(menu.getInitialSource()) : "";
    document.listener(
        value -> {
          dirty = true;
          analysisDelay = LIVE_ANALYSIS_DELAY_TICKS;
          problems = List.of();
          if (editor != null) {
            editor.problems(List.of());
          }
          refreshDiagnostics();
        });
    if (menu.isInitiallyCompiled()) {
      status = "Compiled card · Changed source must be compiled again.";
    }
  }

  @Override
  protected void init() {
    imageWidth = width - 20;
    imageHeight = height - 24;
    super.init();
    int sourceHeight = MIN_SOURCE_HEIGHT;
    int panelHeight = MIN_DIAGNOSTIC_HEIGHT;
    int panelTop = topPos + 39 + sourceHeight + DIVIDER_HEIGHT;
    editor =
        addRenderableWidget(
            new CerebralSourceEditor(
                font, leftPos + 12, topPos + 39, imageWidth - 24, sourceHeight, document));
    tracker =
        addRenderableWidget(
            new CerebralProblems(
                font,
                leftPos + 12,
                panelTop + DIAGNOSTIC_HEADER_HEIGHT,
                imageWidth - 24,
                panelHeight,
                problem -> {
                  editor.goTo(problem.line(), problem.column());
                  setFocused(editor);
                }));
    problemsTab =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Problems"),
                    b -> {
                      showingLog = false;
                      refreshDiagnostics();
                    })
                .bounds(leftPos + 12, panelTop, 112, 12)
                .build());
    logTab =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Error log"),
                    b -> {
                      showingLog = true;
                      refreshDiagnostics();
                    })
                .bounds(leftPos + 128, panelTop, 104, 12)
                .build());
    clearLog =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Clear"),
                    b -> {
                      log.clear();
                      refreshDiagnostics();
                    })
                .bounds(leftPos + imageWidth - 64, panelTop, 52, 12)
                .build());
    diagnosticsToggle =
        addRenderableWidget(
            Button.builder(
                    Component.literal("Hide diagnostics"),
                    b -> {
                      layout.setDiagnosticsHidden(!layout.isDiagnosticsHidden());
                      resizePanels();
                      layout.save();
                    })
                .bounds(leftPos + 244, panelTop, 108, 12)
                .build());
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
                  updateVisibility();
                  setFocused(showingConsole ? console : editor);
                })
            .bounds(leftPos + imageWidth - 112, topPos + 25, 100, 12)
            .build());
    resizePanels();
    setInitialFocus(showingConsole ? console : editor);
  }

  private void analyzeNow() {
    analysisDelay = -1;
    problems =
        EditorAnalysis.analyze(document.text(), ProgramCardData.fingerprint(document.text()));
    editor.problems(problems);
    refreshDiagnostics();
  }

  /** Fractions survive window and GUI-scale changes; only widget bounds change during a drag. */
  private void resizePanels() {
    int areaHeight = imageHeight - 110;
    int available = Math.max(0, areaHeight - DIVIDER_HEIGHT - DIAGNOSTIC_HEADER_HEIGHT);
    int diagnosticHeight =
        layout.isDiagnosticsHidden()
            ? 0
            : Math.max(
                MIN_DIAGNOSTIC_HEIGHT,
                Math.min(
                    available - MIN_SOURCE_HEIGHT,
                    (int) Math.round(available * layout.getDiagnosticFraction())));
    int sourceHeight = Math.max(MIN_SOURCE_HEIGHT, available - diagnosticHeight);
    editor.setHeight(sourceHeight);
    editor.reflow();
    dividerTop = editor.getY() + sourceHeight;
    int panelTop = dividerTop + DIVIDER_HEIGHT;
    tracker.setY(panelTop + DIAGNOSTIC_HEADER_HEIGHT);
    tracker.setHeight(Math.max(1, diagnosticHeight));
    tracker.reflow();
    for (Button button : List.of(problemsTab, logTab, clearLog, diagnosticsToggle)) {
      button.setY(panelTop);
    }
    // Keep header controls separate even at Minecraft's smallest supported GUI width.
    int contentWidth = imageWidth - 24;
    boolean compact = contentWidth < 360;
    int toggleWidth = compact ? 52 : 108;
    int clearWidth = compact ? 40 : 52;
    int tabWidth = Math.min(112, (contentWidth - toggleWidth - clearWidth - 12) / 2);
    int x = leftPos + 12;
    for (Button tab : List.of(problemsTab, logTab)) {
      tab.setX(x);
      tab.setWidth(tabWidth);
      x += tabWidth + 4;
    }
    diagnosticsToggle.setX(x);
    diagnosticsToggle.setWidth(toggleWidth);
    clearLog.setX(leftPos + imageWidth - 12 - clearWidth);
    clearLog.setWidth(clearWidth);
    diagnosticsToggle.setMessage(
        Component.literal(
            (layout.isDiagnosticsHidden() ? "Show" : "Hide") + (compact ? "" : " diagnostics")));
    updateVisibility();
  }

  private boolean overDivider(double mx, double my) {
    return !showingConsole
        && !layout.isDiagnosticsHidden()
        && mx >= editor.getX()
        && mx < editor.getX() + editor.getWidth()
        && my >= dividerTop
        && my < dividerTop + DIVIDER_HEIGHT;
  }

  private void updateVisibility() {
    boolean diagnosticsVisible = !showingConsole && !layout.isDiagnosticsHidden();
    tracker.visible = problemsTab.visible = logTab.visible = clearLog.visible = diagnosticsVisible;
    diagnosticsToggle.visible = !showingConsole;
  }

  private void refreshDiagnostics() {
    if (tracker == null) {
      return;
    }
    String fingerprint = ProgramCardData.fingerprint(document.text());
    tracker.entries(showingLog ? log.entries() : problems, fingerprint, showingLog);
    problemsTab.setMessage(
        Component.literal(analysisDelay > 0 ? "Checking…" : "Problems (" + problems.size() + ")"));
    logTab.setMessage(Component.literal("Error log (" + log.entries().size() + ")"));
    problemsTab.active = showingLog;
    logTab.active = !showingLog;
    clearLog.active = showingLog && !log.entries().isEmpty();
  }

  private void save() {
    PacketDistributor.sendToServer(
        new CardWorkspaceNetwork.Save(menu.containerId, menu.getIdentity(), editor.getValue()));
    status = "Saving…";
  }

  private void compile() {
    analyzeNow();
    PacketDistributor.sendToServer(
        new CardWorkspaceNetwork.Write(menu.containerId, menu.getIdentity(), editor.getValue()));
    log.add(
        EditorAnalysis.logged(
            EditorProblem.Severity.INFO,
            "Compile requested.",
            ProgramCardData.fingerprint(document.text())));
    refreshDiagnostics();
    status = "Compiling on the server…";
  }

  public static void receive(CardWorkspaceNetwork.Result result) {
    if (Minecraft.getInstance().screen instanceof CerebralIdeScreen screen
        && screen.menu.containerId == result.menu()
        && screen.menu.getIdentity().equals(result.identity())) {
      screen.status = result.message();
      screen.log.add(
          EditorAnalysis.logged(
              result.saved() ? EditorProblem.Severity.INFO : EditorProblem.Severity.ERROR,
              result.message(),
              result.fingerprint()));
      if (result.saved() && result.message().startsWith("Compiled and written")) {
        screen.installedFingerprint = result.fingerprint();
        if (result.fingerprint().equals(ProgramCardData.fingerprint(screen.document.text()))) {
          for (var problem : screen.problems) {
            if (problem.severity() == EditorProblem.Severity.WARNING) {
              screen.log.add(problem);
            }
          }
        }
      } else if (result.saved() && !result.fingerprint().equals(screen.installedFingerprint)) {
        screen.installedFingerprint = "";
      }
      if (!result.saved()) {
        screen.showingLog = true;
      }
      screen.refreshDiagnostics();
      if (result.saved()
          && result.fingerprint().equals(ProgramCardData.fingerprint(screen.editor.getValue()))) {
        screen.dirty = false;
      }
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    if (analysisDelay > 0 && --analysisDelay == 0) {
      analyzeNow();
    }
    if (pollTicks++ % STATUS_POLL_INTERVAL_TICKS == 0) {
      PacketDistributor.sendToServer(
          new CardWorkspaceNetwork.Poll(menu.containerId, menu.getIdentity()));
    }
  }

  public static void receiveOutput(CardWorkspaceNetwork.Output result) {
    if (Minecraft.getInstance().screen instanceof CerebralIdeScreen screen
        && screen.menu.containerId == result.menu()
        && screen.menu.getIdentity().equals(result.identity())) {
      if (!screen.runtimeState.equals(result.state())
          && (result.state().startsWith("FAULTED") || result.state().startsWith("Compile error"))) {
        screen.log.add(
            EditorAnalysis.logged(
                EditorProblem.Severity.ERROR, result.state(), screen.installedFingerprint));
        screen.refreshDiagnostics();
      }
      screen.runtimeState = result.state();
      screen.output = result.lines();
      screen.console.lines(result.lines());
    }
  }

  @Override
  public boolean mouseClicked(double mx, double my, int button) {
    if (editor.clickCompletion(mx, my, button)) {
      return true;
    }
    if (button == 0 && overDivider(mx, my)) {
      resizingPanels = true;
      return true;
    }
    var problem = tracker.hovered(mx, my);
    boolean navigable =
        button == 0
            && problem != null
            && problem.line() > 0
            && problem.fingerprint().equals(ProgramCardData.fingerprint(document.text()));
    boolean handled = super.mouseClicked(mx, my, button);
    if (navigable) {
      setFocused(editor);
    }
    return handled;
  }

  @Override
  public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    if (resizingPanels && button == 0) {
      int available = imageHeight - 110 - DIVIDER_HEIGHT - DIAGNOSTIC_HEADER_HEIGHT;
      layout.setDiagnosticFraction((editor.getY() + available - my) / Math.max(1, available));
      resizePanels();
      return true;
    }
    return super.mouseDragged(mx, my, button, dx, dy);
  }

  @Override
  public boolean mouseReleased(double mx, double my, int button) {
    if (resizingPanels && button == 0) {
      resizingPanels = false;
      layout.save();
      return true;
    }
    return super.mouseReleased(mx, my, button);
  }

  @Override
  public void removed() {
    layout.save();
    super.removed();
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (editor.isFocused() && editor.handleCompletionKey(key)) {
      return true;
    }
    if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
      compile();
      return true;
    }
    if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
      save();
      return true;
    }
    if (editor.isFocused() && minecraft.options.keyInventory.matches(key, scan)) {
      return editor.keyPressed(key, scan, modifiers);
    }
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
    if (!showingConsole && !layout.isDiagnosticsHidden()) {
      int color = overDivider(mx, my) || resizingPanels ? 0xffd8a3bd : 0xff815269;
      g.fill(leftPos + 12, dividerTop + 3, leftPos + imageWidth - 12, dividerTop + 5, color);
      g.fill(
          leftPos + imageWidth / 2 - 10,
          dividerTop + 1,
          leftPos + imageWidth / 2 + 10,
          dividerTop + 7,
          color);
    }
    g.drawString(
        font,
        font.plainSubstrByWidth(showingConsole ? runtimeState : status, imageWidth - 24),
        leftPos + 12,
        topPos + imageHeight - 61,
        OrganScreenStyle.TEXT,
        false);
    g.drawString(
        font,
        font.plainSubstrByWidth(
            "Ctrl+S save · Ctrl+Enter compile · Ctrl+Space suggest · Drag divider to resize",
            imageWidth - 24),
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
    editor.renderCompletions(g);
    if (overDivider(mx, my) && !resizingPanels) {
      g.renderTooltip(font, Component.literal("Drag to resize the editor and diagnostics"), mx, my);
    }
    var hovered = editor.hoveredProblem(mx, my);
    if (hovered != null) {
      g.renderTooltip(font, Component.literal(hovered.message()), mx, my);
    }
    var diagnostic = tracker.hovered(mx, my);
    if (diagnostic != null) {
      String detail = diagnostic.message();
      if (!diagnostic.fingerprint().isEmpty()
          && !diagnostic.fingerprint().equals(ProgramCardData.fingerprint(document.text()))) {
        detail += " (Earlier source revision; navigation disabled.)";
      }
      g.renderTooltip(font, Component.literal(detail), mx, my);
    }
    if (mx >= leftPos + 12
        && mx < leftPos + imageWidth - 12
        && my >= topPos + imageHeight - 63
        && my < topPos + imageHeight - 50) {
      g.renderTooltip(font, Component.literal(showingConsole ? runtimeState : status), mx, my);
    }
  }
}
