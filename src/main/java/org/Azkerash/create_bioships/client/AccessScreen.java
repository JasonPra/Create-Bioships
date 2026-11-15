package org.Azkerash.create_bioships.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.AccessMenu;

public class AccessScreen extends AbstractContainerScreen<AccessMenu> {
  private int page;
  private final List<Button> entries = new ArrayList<>();

  public AccessScreen(AccessMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 250;
    imageHeight = 220;
  }

  @Override
  protected void init() {
    super.init();
    buttons();
  }

  private void buttons() {
    clearWidgets();
    entries.clear();
    for (int row = 0; row < 7; row++) {
      int index = page * 7 + row;
      if (index >= menu.choices.size()) break;
      var button =
          Button.builder(
                  Component.empty(),
                  b -> {
                    if (minecraft.gameMode != null)
                      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                  })
              .bounds(leftPos + 12, topPos + 35 + row * 21, 226, 20)
              .build();
      entries.add(button);
      addRenderableWidget(button);
    }
    addRenderableWidget(
        Button.builder(
                Component.literal("<"),
                b -> {
                  if (page > 0) {
                    page--;
                    buttons();
                  }
                })
            .bounds(leftPos + 12, topPos + 190, 30, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal(">"),
                b -> {
                  if ((page + 1) * 7 < menu.choices.size()) {
                    page++;
                    buttons();
                  }
                })
            .bounds(leftPos + 208, topPos + 190, 30, 20)
            .build());
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff392331);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xff201b20);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 12, 10, 0xffe6d3bd, false);
    g.drawString(font, "Players and nearby named animals", 12, 23, 0xffb5a996, false);
    g.drawString(font, "Creator always has access", 50, 196, 0xffb5a996, false);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    for (int i = 0; i < entries.size(); i++) {
      int index = page * 7 + i;
      var choice = menu.choices.get(index);
      String label =
          (menu.allowed(index) ? "[Allowed] " : "[Blocked] ")
              + (choice.animal() ? "Animal: " : "")
              + choice.label();
      entries.get(i).setMessage(Component.literal(label));
    }
    super.render(g, mouseX, mouseY, partial);
  }
}
