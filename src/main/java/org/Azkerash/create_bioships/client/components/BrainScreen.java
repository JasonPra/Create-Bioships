package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.brain.BrainMenu;

public final class BrainScreen extends AbstractContainerScreen<BrainMenu> {
  public BrainScreen(BrainMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 228;
    imageHeight = 210;
    inventoryLabelX = 33;
    inventoryLabelY = 114;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    OrganScreenStyle.panel(g, leftPos, topPos, imageWidth, imageHeight);
    g.renderItem(
        ComponentRegistry.BRAIN.get().asItem().getDefaultInstance(), leftPos + 12, topPos + 8);
    g.drawCenteredString(
        font,
        menu.capacity() + " / 27 behavior slots",
        leftPos + 114,
        topPos + 31,
        OrganScreenStyle.MUTED);
    for (int i = 0; i < menu.slots.size(); i++) {
      var slot = menu.slots.get(i);
      OrganScreenStyle.slot(g, leftPos + slot.x, topPos + slot.y);
      if (i < 27 && i >= menu.capacity() && !slot.hasItem()) {
        g.fill(
            leftPos + slot.x,
            topPos + slot.y,
            leftPos + slot.x + 16,
            topPos + slot.y + 16,
            0xc0301c2a);
        g.fill(
            leftPos + slot.x + 5,
            topPos + slot.y + 7,
            leftPos + slot.x + 11,
            topPos + slot.y + 9,
            0xff674456);
      }
    }
    g.drawCenteredString(
        font,
        "One connected brain · one shared memory",
        leftPos + 114,
        topPos + 103,
        OrganScreenStyle.MUTED);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 34, 11, OrganScreenStyle.TEXT, false);
    g.drawString(
        font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, OrganScreenStyle.TEXT, false);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
  }
}
