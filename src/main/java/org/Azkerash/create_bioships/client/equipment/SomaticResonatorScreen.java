package org.Azkerash.create_bioships.client.equipment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.client.development.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.equipment.DiagnosticAccess;

/** Two radial sectors toggle independent overlays; the centre closes the instrument wheel. */
public final class SomaticResonatorScreen extends Screen {
  private static final int OUTER = 92, INNER = 34;

  private SomaticResonatorScreen() {
    super(Component.literal("Somatic Resonator"));
  }

  public static void open() {
    var mc = Minecraft.getInstance();
    if (DiagnosticAccess.allowed(mc.player)) {
      mc.setScreen(new SomaticResonatorScreen());
    }
  }

  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void tick() {
    if (!DiagnosticAccess.allowed(Minecraft.getInstance().player)) {
      onClose();
    }
  }

  private int sector(double x, double y) {
    double dx = x - width / 2.0, dy = y - height / 2.0, r = Math.hypot(dx, dy);
    return r < INNER ? 0 : r > OUTER ? -1 : dx < 0 ? 1 : 2;
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button != 0) {
      return super.mouseClicked(x, y, button);
    }
    return switch (sector(x, y)) {
      case 0 -> {
        onClose();
        yield true;
      }
      case 1 -> {
        FacingDebugOverlay.toggle();
        yield true;
      }
      case 2 -> {
        IntestineDebugOverlay.toggle();
        yield true;
      }
      default -> false;
    };
  }

  private static void ring(
      GuiGraphics g, int cx, int cy, int outer, int inner, int left, int right) {
    for (int y = -outer; y <= outer; y++) {
      int edge = (int) Math.sqrt(outer * outer - y * y);
      int hole = Math.abs(y) < inner ? (int) Math.sqrt(inner * inner - y * y) : 0;
      if (edge <= Math.max(hole, 2)) {
        continue;
      }
      g.fill(cx - edge, cy + y, cx - Math.max(hole, 2), cy + y + 1, left);
      g.fill(cx + Math.max(hole, 2), cy + y, cx + edge, cy + y + 1, right);
    }
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    g.fill(0, 0, width, height, 0x7708000c);
    int cx = width / 2, cy = height / 2, hover = sector(mouseX, mouseY);
    ring(g, cx, cy, OUTER + 3, INNER - 3, 0xffc6ad8b, 0xffc6ad8b);
    int left = FacingDebugOverlay.enabled() ? 0xff20564f : 0xff342538,
        right = IntestineDebugOverlay.enabled() ? 0xff773650 : 0xff432633;
    ring(g, cx, cy, OUTER, INNER, hover == 1 ? 0xff37796c : left, hover == 2 ? 0xffa34c70 : right);
    // The centre is a biological sensing core rather than a third diagnostic mode.
    for (int y = -INNER + 5; y < INNER - 5; y++) {
      int half = (int) Math.sqrt((INNER - 5) * (INNER - 5) - y * y);
      g.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xff261321);
    }
    g.pose().pushPose();
    g.pose().translate(cx - 16, cy - 16, 0);
    g.pose().scale(2, 2, 2);
    g.renderItem(Create_bioships.SOMATIC_RESONATOR.get().getDefaultInstance(), 0, 0);
    g.pose().popPose();
    g.drawCenteredString(font, title, cx, cy - OUTER - 20, 0xffead5bf);
    g.drawCenteredString(font, "➤", cx - 61, cy - 40, 0xffa4ece0);
    g.renderItem(ComponentRegistry.STOMACH_ITEM.get().getDefaultInstance(), cx + 53, cy - 43);
    var leftLabel = new String[] {"Show", "sublevel", "facing", "direction"};
    var rightLabel = new String[] {"Show", "digestion", "network", "connections"};
    for (int i = 0; i < 4; i++) {
      g.drawCenteredString(font, leftLabel[i], cx - 61, cy - 18 + i * 10, 0xffead5bf);
      g.drawCenteredString(font, rightLabel[i], cx + 61, cy - 18 + i * 10, 0xffead5bf);
    }
    g.drawCenteredString(
        font, FacingDebugOverlay.enabled() ? "Visible" : "Hidden", cx - 61, cy + 30, 0xffa4ece0);
    g.drawCenteredString(
        font, IntestineDebugOverlay.enabled() ? "Visible" : "Hidden", cx + 61, cy + 30, 0xffefb3c4);
    g.drawCenteredString(font, "Overlays only · Centre to close", cx, cy + OUTER + 14, 0xffc6ad8b);
  }
}
