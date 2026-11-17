package org.Azkerash.create_bioships.disabled.wip.client;

import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.disabled.wip.BlueprintMenu;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.Azkerash.create_bioships.*;
import org.Azkerash.create_bioships.BioshipTissue;

public class BlueprintScreen extends AbstractContainerScreen<BlueprintMenu> {
  private int mode = 1, roomPage;

  public BlueprintScreen(BlueprintMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 374;
    imageHeight = 228;
  }

  private void send(int id) {
    if (id >= 100 || id == 3) menu.edit(id);
    if (minecraft.gameMode != null)
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    if (id >= 100 || id == 3) {
      roomPage = menu.draft.selected / 7;
      init(minecraft, width, height);
    }
  }

  @Override
  protected void init() {
    super.init();
    for (int row = 0; row < 7; row++) {
      int i = roomPage * 7 + row;
      if (i >= menu.draft.rooms.size()) break;
      final int n = i;
      addRenderableWidget(
          Button.builder(
                  Component.literal(
                      (menu.draft.selected == i ? "> " : "") + menu.draft.rooms.get(i).name()),
                  b -> send(100 + n))
              .bounds(leftPos + 8, topPos + 28 + row * 12, 160, 12)
              .build());
    }
    addRenderableWidget(
        Button.builder(
                Component.literal("<"),
                b -> {
                  if (roomPage > 0) {
                    roomPage--;
                    init(minecraft, width, height);
                  }
                })
            .bounds(leftPos + 110, topPos + 112, 28, 14)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal(">"),
                b -> {
                  if ((roomPage + 1) * 7 < menu.draft.rooms.size()) {
                    roomPage++;
                    init(minecraft, width, height);
                  }
                })
            .bounds(leftPos + 140, topPos + 112, 28, 14)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("R"), b -> send(3))
            .bounds(leftPos + 343, topPos + 30, 23, 18)
            .tooltip(
                net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("Rotate a new room or corridor")))
            .build());
    String[] labels = {
      "Move room", "Add room", "Add corridor", "Clear opening", "Add wall", "Add door"
    };
    for (int i = 0; i < labels.length; i++) {
      final int m = i + 1;
      addRenderableWidget(
          Button.builder(Component.literal(labels[i]), b -> mode = m)
              .bounds(leftPos + 8 + (i % 2) * 82, topPos + 130 + (i / 2) * 18, 80, 18)
              .build());
    }
    addRenderableWidget(
        Button.builder(
                Component.literal("\u2713").withStyle(net.minecraft.ChatFormatting.GREEN),
                b -> send(1))
            .bounds(leftPos + 294, topPos + 202, 32, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("\u2715").withStyle(net.minecraft.ChatFormatting.RED),
                b -> send(2))
            .bounds(leftPos + 332, topPos + 202, 32, 20)
            .build());
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int gx = (int) (x - leftPos - 190), gz = (int) (y - topPos - 30);
    if (button == 0 && gx >= 0 && gz >= 0 && gx < 147 && gz < 147) {
      send(mode * 2500 + (gz / 3) * 49 + gx / 3);
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff271923);
    g.fill(leftPos + 187, topPos + 27, leftPos + 340, topPos + 180, 0xff110f12);
    for (var r : menu.draft.rooms) {
      int x = leftPos + 190 + (r.x() + 24 - r.width() / 2) * 3,
          z = topPos + 30 + (r.z() + 24 - r.length() / 2) * 3;
      g.fill(x, z, x + r.width() * 3, z + r.length() * 3, r.id() < 2 ? 0xff824d66 : 0xff50685d);
      g.renderOutline(
          x,
          z,
          r.width() * 3,
          r.length() * 3,
          menu.draft.rooms.indexOf(r) == menu.draft.selected ? 0xffeeee88 : 0xffbaa594);
    }
    menu.model
        .blueprint()
        .proposal(menu.model, menu.draft)
        .forEach(
            (p, s) -> {
              if (p.getY() != 2) return;
              int x = leftPos + 190 + (p.getX() + 24) * 3, z = topPos + 30 + (p.getZ() + 24) * 3;
              if (x < leftPos + 190 || z < topPos + 30 || x >= leftPos + 337 || z >= topPos + 177)
                return;
              int color =
                  BioshipTissue.isTissue(s) || s.is(Create_bioships.CARAPACE.get())
                      ? 0xffb77b91
                      : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                              .getKey(s.getBlock())
                              .getPath()
                              .contains("membrane")
                          ? 0xff62d5b6
                          : 0xffe1c57d;
              g.fill(x, z, x + 3, z + 3, color);
            });
    menu.draft.edits.forEach(
        (p, k) -> {
          int x = leftPos + 190 + (p.getX() + 24) * 3, z = topPos + 30 + (p.getZ() + 24) * 3;
          g.fill(x, z, x + 3, z + 3, k == 0 ? 0xff202020 : k == 1 ? 0xffc48293 : 0xff62d5b6);
        });
    int cx = leftPos + 190 + 24 * 3, cz = topPos + 30 + 24 * 3;
    g.fill(cx, cz, cx + 3, cz + 3, 0xffffcc44);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 8, 8, 0xffe6d3bd, false);
    g.drawString(
        font,
        "Biomass: " + menu.available() + "  Required: " + menu.cost(),
        8,
        19,
        menu.cost() > menu.available() ? 0xffff7777 : 0xffa8df9a,
        false);
    g.drawString(font, "North ^   Draft floor plan", 190, 16, 0xffc9b7b0, false);
    g.drawString(
        font,
        "Tool: "
            + switch (mode) {
              case 1 -> "move selected room";
              case 2 -> "add room";
              case 3 -> "add corridor";
              case 4 -> "remove wall / door";
              case 5 -> "add wall";
              default -> "add membrane door";
            },
        8,
        187,
        0xffe6d3bd,
        false);
    g.drawString(font, "Click map to edit. X discards.", 8, 199, 0xffb5a996, false);
    g.drawString(font, "Confirm: land, evacuate, rebuild.", 8, 213, 0xffb5a996, false);
  }
}
