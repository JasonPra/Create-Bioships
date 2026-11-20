package org.Azkerash.create_bioships.client.components;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flesh-and-bone button skin; vanilla focus, narration and input behavior stay intact. */
public final class OrganButton extends Button {
  private BooleanSupplier selected = () -> false;
  private int accent = 0xffc18d71;

  public OrganButton(int x, int y, int width, int height, String label, OnPress action) {
    super(x, y, width, height, Component.literal(label), action, DEFAULT_NARRATION);
  }

  public OrganButton selected(BooleanSupplier value) {
    selected = value;
    return this;
  }

  public OrganButton accent(int value) {
    accent = value;
    return this;
  }

  @Override
  protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
    int x = getX(), y = getY(), w = getWidth(), h = getHeight();
    boolean lit = active && (isHoveredOrFocused() || selected.getAsBoolean());
    int edge = active ? (lit ? accent : 0xff92516a) : 0xff4b3040;
    g.fill(x + 2, y, x + w - 2, y + h, edge);
    g.fill(x, y + 2, x + w, y + h - 2, edge);
    g.fillGradient(
        x + 2,
        y + 2,
        x + w - 2,
        y + h - 2,
        lit ? 0xff71344e : 0xff492237,
        lit ? 0xff421b31 : 0xff281421);
    g.fill(x + 3, y + 2, x + w - 3, y + 3, lit ? 0xffd2938f : 0xff99526b);
    g.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, 0xff20111d);
    int bone = active ? 0xffcbb792 : 0xff695460;
    g.fill(x + 1, y + 2, x + 3, y + 5, bone);
    g.fill(x + w - 3, y + h - 5, x + w - 1, y + h - 2, bone);
    if (selected.getAsBoolean()) {
      g.fill(x + 6, y + h - 3, x + w - 6, y + h - 2, accent);
    }
    g.drawCenteredString(
        Minecraft.getInstance().font,
        getMessage(),
        x + w / 2,
        y + (h - 8) / 2,
        active ? (lit ? 0xffffe5bf : OrganScreenStyle.TEXT) : 0xff937c89);
  }
}
