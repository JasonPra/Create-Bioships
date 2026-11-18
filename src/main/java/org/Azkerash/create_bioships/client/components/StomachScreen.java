package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.components.logic.stomach.StomachMenu;

public final class StomachScreen extends AbstractContainerScreen<StomachMenu> {
  public StomachScreen(StomachMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 228;
    imageHeight = 258;
    inventoryLabelX = 40;
    inventoryLabelY = 164;
  }

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    g.fill(x, y, x + imageWidth, y + imageHeight, 0xff482635);
    g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xff22171c);
    g.drawString(
        font, "Stomach acid: " + menu.value(0) + " / 1000", x + 12, y + 26, 0xffd9c9b4, false);
    bar(g, x + 12, y + 39, 204, menu.value(0), 1000, 0xff96ad5d);
    g.drawString(
        font,
        "Digestion: " + menu.value(1) + " / " + menu.value(2),
        x + 12,
        y + 56,
        0xffd9c9b4,
        false);
    bar(g, x + 12, y + 69, 204, menu.value(1), Math.max(1, menu.value(2)), 0xffc98860);
    g.drawString(font, "Food queue", x + 12, y + 83, 0xffd9c9b4, false);
    g.drawString(font, "Components / paste", x + 104, y + 83, 0xffd9c9b4, false);
    for (var slot : menu.slots)
      g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xff635049);
    g.drawString(font, "Intake through mouth only", x + 12, y + 153, 0xffa79a91, false);
  }

  private void bar(GuiGraphics g, int x, int y, int width, int value, int max, int color) {
    g.fill(x, y, x + width, y + 10, 0xff483d32);
    g.fill(x + 1, y + 1, x + 1 + (width - 2) * Math.clamp(value, 0, max) / max, y + 9, color);
  }

  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
