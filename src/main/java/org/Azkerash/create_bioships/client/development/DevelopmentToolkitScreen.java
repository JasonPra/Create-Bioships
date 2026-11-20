package org.Azkerash.create_bioships.client.development;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A single item menu replaces our debug key combinations. */
public final class DevelopmentToolkitScreen extends Screen {
  private DevelopmentToolkitScreen() {
    super(Component.literal("Development Toolkit"));
  }

  public static void open() {
    Minecraft.getInstance().setScreen(new DevelopmentToolkitScreen());
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private void control(String text, Runnable action, int row) {
    addRenderableWidget(
        Button.builder(
                Component.literal(text),
                b -> {
                  action.run();
                  rebuildWidgets();
                })
            .bounds((width - 244) / 2, (height - 249) / 2 + 37 + row * 27, 244, 22)
            .build());
  }

  @Override
  protected void init() {
    control("Paths: " + (PathDebugOverlay.enabled() ? "ON" : "OFF"), PathDebugOverlay::toggle, 0);
    control(
        "Vision rays: " + (VisionDebugOverlay.enabled() ? "ON" : "OFF"),
        VisionDebugOverlay::toggle,
        1);
    control(
        "Intestine connections: " + (IntestineDebugOverlay.enabled() ? "ON" : "OFF"),
        IntestineDebugOverlay::toggle,
        2);
    var renderer = Minecraft.getInstance().getEntityRenderDispatcher();
    control(
        "Hitboxes: " + (renderer.shouldRenderHitBoxes() ? "ON" : "OFF"),
        () -> renderer.setRenderHitBoxes(!renderer.shouldRenderHitBoxes()),
        3);
    control(
        "Creature statistics: " + (CreatureStatsOverlay.enabled() ? "ON" : "OFF"),
        CreatureStatsOverlay::toggle,
        5);
    control(
        "Front arrows: " + (FacingDebugOverlay.enabled() ? "ON" : "OFF"),
        FacingDebugOverlay::toggle,
        4);
    control("Done", this::onClose, 6);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    g.fill(0, 0, width, height, 0x99000000);
    int y = (height - 249) / 2;
    g.drawCenteredString(font, title, width / 2, y + 12, 0xffffffff);
    for (var widget : renderables) {
      widget.render(g, mx, my, partial);
    }
  }
}
