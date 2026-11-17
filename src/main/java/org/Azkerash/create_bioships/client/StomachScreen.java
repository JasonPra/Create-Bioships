package org.Azkerash.create_bioships.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.StomachMenu;

public class StomachScreen extends AbstractContainerScreen<StomachMenu> {
  public StomachScreen(StomachMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 220;
    imageHeight = 218;
  }

  @Override
  protected void init() {
    super.init();
    addRenderableWidget(
        Button.builder(Component.literal("<"), button -> page(0))
            .bounds(leftPos + 12, topPos + 172, 22, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal(">"), button -> page(1))
            .bounds(leftPos + 186, topPos + 172, 22, 20)
            .build());
  }

  private void page(int direction) {
    if (minecraft != null && minecraft.gameMode != null)
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, direction);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    g.fill(x, y, x + imageWidth, y + imageHeight, 0xff482635);
    g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xff22171c);
    int members = Math.max(1, menu.value(10)), maximum = members * 1000;
    g.drawString(
        font, "Food " + menu.value(6) + " / " + menu.value(9), x + 12, y + 29, 0xffd9c9b4, false);
    g.fill(x + 12, y + 43, x + 208, y + 54, 0xff483d32);
    g.fill(x + 13, y + 44, x + 13 + (int) (194L * menu.value(0) / maximum), y + 53, 0xff96ad5d);
    g.drawString(
        font, "Acid " + menu.value(0) + " / " + maximum, x + 24, y + 45, 0xffffffff, false);
    g.drawString(
        font,
        "Digesting " + menu.value(1) + " / " + menu.value(2),
        x + 12,
        y + 63,
        0xffd9c9b4,
        false);
    g.drawString(
        font,
        "Flesh " + menu.value(3) + " | Bone " + menu.value(4),
        x + 12,
        y + 77,
        0xffcbb9b2,
        false);
    g.drawString(
        font,
        "Fibers " + menu.value(5) + " | Sacs " + menu.value(7),
        x + 12,
        y + 90,
        0xffcbb9b2,
        false);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++) {
        int sx = x + 28 + column * 18, sy = y + 111 + row * 18;
        g.fill(sx, sy, sx + 18, sy + 18, 0xff624753);
        g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xff1a1116);
      }
    String pages = "Page " + (menu.value(12) + 1) + " / " + Math.max(1, menu.value(13));
    g.drawCenteredString(font, pages, x + 110, y + 178, 0xffd9c9b4);
    g.drawString(
        font,
        members
            + " stomach"
            + (members == 1 ? "" : "s")
            + (menu.value(11) == 1 ? " | 25% faster" : ""),
        x + 12,
        y + 199,
        0xffa7c281,
        false);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(
        font,
        menu.value(11) == 1 ? Component.literal("Large stomach") : title,
        12,
        10,
        0xffead8c9,
        false);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
