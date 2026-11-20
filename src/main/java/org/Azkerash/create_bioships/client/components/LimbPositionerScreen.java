package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.compat.letitwalk.*;

/** Rest-pose and passive articulation controls for our compact and modular limbs only. */
public final class LimbPositionerScreen extends Screen {
  private LimbPositionerState state;

  private LimbPositionerScreen(LimbPositionerState state) {
    super(Component.translatable("item.create_bioships.limb_positioner"));
    this.state = state;
  }

  public static void receive(LimbPositionerState state) {
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof LimbPositionerScreen screen
        && screen.state.pos().equals(state.pos())) {
      screen.state = state;
      screen.rebuildWidgets();
    } else {
      mc.setScreen(new LimbPositionerScreen(state));
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  protected void init() {
    int x = (width - 252) / 2, y = (height - 196) / 2;
    for (int row = 0; row < 4; row++) {
      if (row == 0 && !state.compact() || row >= 2 && !state.assembled()) {
        continue;
      }
      final int setting = row;
      for (int sign : new int[] {-1, 1}) {
        addRenderableWidget(
            Button.builder(
                    Component.literal(sign < 0 ? "−" : "+"),
                    b ->
                        PacketDistributor.sendToServer(
                            new LimbPositionerPayload(
                                state.pos(), setting, sign * (setting == 1 ? 5 : 1))))
                .bounds(x + (sign < 0 ? 184 : 214), y + 42 + row * 30, 24, 20)
                .build());
      }
    }
    addRenderableWidget(
        Button.builder(
                Component.literal("Reset"),
                b ->
                    PacketDistributor.sendToServer(
                        new LimbPositionerPayload(state.pos(), LimbPositionerActions.RESET, 0)))
            .bounds(x + 28, y + 169, 96, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x + 128, y + 169, 96, 20)
            .build());
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    renderBackground(g, mx, my, partial);
    int x = (width - 252) / 2, y = (height - 196) / 2;
    OrganScreenStyle.panel(g, x, y, 252, 196);
    g.drawCenteredString(font, title, x + 126, y + 10, OrganScreenStyle.TEXT);
    g.drawCenteredString(
        font,
        state.compact() ? "Compact armored leg" : "Modular joint",
        x + 126,
        y + 25,
        OrganScreenStyle.MUTED);
    String[] labels = {
      state.compact() ? "Height: " + state.height() + " blocks" : "Height: built from segments",
      "Rest pose: " + state.angle() + "°",
      state.assembled()
          ? String.format(java.util.Locale.ROOT, "Passive stiffness: %.2f×", state.stiffness())
          : "Assemble to tune joints",
      String.format(java.util.Locale.ROOT, "Passive damping: %.2f×", state.damping())
    };
    for (int row = 0; row < 4; row++) {
      g.drawString(font, labels[row], x + 12, y + 48 + row * 30, OrganScreenStyle.TEXT, false);
    }
    for (var widget : renderables) {
      widget.render(g, mx, my, partial);
    }
  }
}
