package org.azkerash.createbioships.client.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.brain.BrainMenu;

public final class BrainScreen extends AbstractContainerScreen<BrainMenu> {
  private int selected;
  private net.minecraft.client.gui.components.Button edit;

  public BrainScreen(BrainMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 228;
    imageHeight = 238;
    inventoryLabelX = 33;
    inventoryLabelY = 114;
  }

  @Override
  protected void init() {
    super.init();
    addRenderableWidget(
        net.minecraft.client.gui.components.Button.builder(
                Component.literal("<"),
                b -> {
                  selected = Math.floorMod(selected - 1, Math.max(1, menu.capacity()));
                  updateEditor();
                })
            .bounds(leftPos + 20, topPos + imageHeight - 24, 20, 20)
            .build());
    edit =
        addRenderableWidget(
            net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Edit card 1"),
                    b ->
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new org.azkerash.createbioships.components.logic.brain
                                .CardWorkspaceNetwork.Open(menu.containerId, selected)))
                .bounds(leftPos + 44, topPos + imageHeight - 24, 140, 20)
                .build());
    addRenderableWidget(
        net.minecraft.client.gui.components.Button.builder(
                Component.literal(">"),
                b -> {
                  selected = (selected + 1) % Math.max(1, menu.capacity());
                  updateEditor();
                })
            .bounds(leftPos + 188, topPos + imageHeight - 24, 20, 20)
            .build());
    updateEditor();
  }

  private void updateEditor() {
    if (edit == null) {
      return;
    }
    selected = Math.min(selected, Math.max(0, menu.capacity() - 1));
    edit.setMessage(Component.literal("Edit card " + (selected + 1) + " · C#"));
    edit.active =
        selected < menu.capacity()
            && org.azkerash.createbioships.core.brain.program.ProgramCardData.editable(
                menu.getSlot(selected).getItem());
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    updateEditor();
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
