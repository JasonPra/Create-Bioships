package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.compat.letitwalk.SocketJointPayload;

/** Per-joint range controls, separate from the compact attachment block's general settings. */
public final class SocketJointScreen extends Screen {
  private SocketJointPayload.State state;

  private SocketJointScreen(SocketJointPayload.State state) {
    super(Component.literal("Synovial Socket"));
    this.state = state;
  }

  public static void receive(SocketJointPayload.State state) {
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof SocketJointScreen screen
        && screen.state.pos().equals(state.pos())
        && screen.state.joint() == state.joint()) {
      screen.state = state;
      screen.rebuildWidgets();
    } else {
      mc.setScreen(new SocketJointScreen(state));
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private void send(int setting, int delta) {
    PacketDistributor.sendToServer(
        new SocketJointPayload(state.pos(), state.joint(), setting, delta));
  }

  @Override
  protected void init() {
    int x = (width - 252) / 2, y = (height - 160) / 2;
    for (int row = 0; row < 2; row++) {
      final int setting = row;
      for (int sign : new int[] {-1, 1}) {
        addRenderableWidget(
            Button.builder(Component.literal(sign < 0 ? "−" : "+"), b -> send(setting, sign))
                .bounds(x + (sign < 0 ? 184 : 214), y + 48 + row * 32, 24, 20)
                .build());
      }
    }
    addRenderableWidget(
        Button.builder(Component.literal("Reset joint"), b -> send(2, 0))
            .bounds(x + 16, y + 129, 106, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x + 130, y + 129, 106, 20)
            .build());
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    renderBackground(g, mx, my, partial);
    int x = (width - 252) / 2, y = (height - 160) / 2;
    OrganScreenStyle.panel(g, x, y, 252, 160);
    g.drawCenteredString(font, title, x + 126, y + 10, OrganScreenStyle.TEXT);
    g.drawCenteredString(
        font,
        state.compact() ? "Compact leg · Joint " + (state.joint() + 1) : "Modular socket",
        x + 126,
        y + 27,
        OrganScreenStyle.MUTED);
    g.drawString(
        font,
        "Swivel range: " + state.swivel() + "°",
        x + 12,
        y + 54,
        OrganScreenStyle.TEXT,
        false);
    g.drawString(
        font, "Bend limit: ±" + state.bend() + "°", x + 12, y + 86, OrganScreenStyle.TEXT, false);
    g.drawCenteredString(
        font, "Limits apply to this joint only", x + 126, y + 112, OrganScreenStyle.MUTED);
    for (var widget : renderables) {
      widget.render(g, mx, my, partial);
    }
  }
}
