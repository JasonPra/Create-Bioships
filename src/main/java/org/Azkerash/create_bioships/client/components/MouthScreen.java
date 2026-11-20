package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.MouthMenu;

public final class MouthScreen extends AbstractContainerScreen<MouthMenu> {
  public MouthScreen(MouthMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 228;
    imageHeight = 222;
    inventoryLabelX = 33;
    inventoryLabelY = 128;
  }

  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    OrganScreenStyle.panel(g, x, y, imageWidth, imageHeight);
    g.renderItem(ComponentRegistry.MOUTH.get().asItem().getDefaultInstance(), x + 12, y + 8);
    g.drawCenteredString(font, "FOOD INTAKE", x + 114, y + 35, OrganScreenStyle.MUTED);
    g.fill(x + 82, y + 49, x + 146, y + 110, 0xff210e20);
    for (var slot : menu.slots) {
      OrganScreenStyle.slot(g, x + slot.x, y + slot.y);
    }
    g.drawCenteredString(font, "Mouth → gut → stomach", x + 114, y + 114, OrganScreenStyle.MUTED);
    OrganScreenStyle.divider(g, x + 12, y + 125, imageWidth - 24);
  }

  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 34, 11, OrganScreenStyle.TEXT, false);
    g.drawString(
        font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, OrganScreenStyle.TEXT, false);
  }

  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
