package org.Azkerash.create_bioships.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.StomachMenu;

public class StomachScreen extends AbstractContainerScreen<StomachMenu> {
  public StomachScreen(StomachMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 220;
    imageHeight = 172;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    g.fill(x, y, x + imageWidth, y + imageHeight, 0xff482635);
    g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xff22171c);
    g.drawString(font, "Stomach acid", x + 12, y + 28, 0xffd9c9b4, false);
    g.fill(x + 12, y + 42, x + 208, y + 55, 0xff483d32);
    g.fill(x + 13, y + 43, x + 13 + 194 * menu.value(0) / 1000, y + 54, 0xff96ad5d);
    g.drawString(font, menu.value(0) + " / 1000", x + 78, y + 45, 0xffffffff, false);
    g.drawString(
        font,
        "Digestion: " + menu.value(1) + " / " + menu.value(2),
        x + 12,
        y + 65,
        0xffd9c9b4,
        false);
    g.drawString(font, "Internal tissue reserves", x + 12, y + 84, 0xffe6d6c6, false);
    g.drawString(
        font,
        "Flesh " + menu.value(3) + "   Bone " + menu.value(4),
        x + 12,
        y + 100,
        0xffcbb9b2,
        false);
    g.drawString(font, "Elastic fibers " + menu.value(5), x + 12, y + 115, 0xffcbb9b2, false);
    g.drawString(
        font,
        "Food " + menu.value(6) + " / 576  |  Sacs " + menu.value(7),
        x + 12,
        y + 133,
        0xffcbb9b2,
        false);
    g.drawString(font, "Reserves cannot be extracted", x + 12, y + 153, 0xffa79a91, false);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 12, 10, 0xffead8c9, false);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
