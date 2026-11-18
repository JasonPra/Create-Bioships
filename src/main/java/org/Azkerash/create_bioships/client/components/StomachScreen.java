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
    OrganScreenStyle.panel(g, x, y, imageWidth, imageHeight);
    g.renderItem(
        org.Azkerash.create_bioships
            .components
            .ComponentRegistry
            .STOMACH_ITEM
            .get()
            .getDefaultInstance(),
        x + imageWidth - 28,
        y + 8);
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
    for (var slot : menu.slots) OrganScreenStyle.slot(g, x + slot.x, y + slot.y);
    OrganScreenStyle.divider(g, x + 12, y + 162, imageWidth - 24);
    g.drawString(
        font,
        "Longevity " + menu.value(3) + "/1 · Efficiency " + menu.value(4) + "/3",
        x + 12,
        y + 153,
        0xffa79a91,
        false);
  }

  private void bar(GuiGraphics g, int x, int y, int width, int value, int max, int color) {
    OrganScreenStyle.gauge(g, x, y, width, value, max, color);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 12, 8, 0xffead8c9, false);
    g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffd9c9b4, false);
  }

  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
