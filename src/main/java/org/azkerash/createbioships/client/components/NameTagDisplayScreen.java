package org.azkerash.createbioships.client.components;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.nametag.*;

public final class NameTagDisplayScreen extends AbstractContainerScreen<NameTagDisplayMenu> {
  private final OffsetSlider[] sliders = new OffsetSlider[3];

  public NameTagDisplayScreen(NameTagDisplayMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 228;
    imageHeight = 250;
    inventoryLabelX = 33;
    inventoryLabelY = 148;
  }

  @Override
  protected void init() {
    super.init();
    for (int i = 0; i < 3; i++) {
      sliders[i] =
          addRenderableWidget(
              new OffsetSlider(i, leftPos + 32, topPos + 73 + i * 21, menu.getOffsets().get(i)));
    }
    addRenderableWidget(
        new OrganButton(
            leftPos + 164,
            topPos + 42,
            44,
            18,
            "Reset",
            button -> {
              for (int i = 0; i < 3; i++) {
                sliders[i].reset(i == 1 ? NameTagDisplayEntity.DEFAULT_Y_UNITS : 0);
              }
              send();
            }));
  }

  private void send() {
    PacketDistributor.sendToServer(
        new NameTagDisplayConfig(
            menu.containerId, sliders[0].units(), sliders[1].units(), sliders[2].units()));
  }

  private final class OffsetSlider extends AbstractSliderButton {
    final int axis;

    OffsetSlider(int axis, int x, int y, int units) {
      super(
          x,
          y,
          164,
          18,
          Component.empty(),
          (units - NameTagDisplayEntity.MIN)
              / (double) (NameTagDisplayEntity.MAX - NameTagDisplayEntity.MIN));
      this.axis = axis;
      updateMessage();
    }

    int units() {
      return (int)
          Math.round(
              NameTagDisplayEntity.MIN
                  + value * (NameTagDisplayEntity.MAX - NameTagDisplayEntity.MIN));
    }

    void reset(int units) {
      value =
          (units - NameTagDisplayEntity.MIN)
              / (double) (NameTagDisplayEntity.MAX - NameTagDisplayEntity.MIN);
      updateMessage();
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
      int x = getX(), y = getY();
      g.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? 0xffb78b9d : 0xff6c2848);
      g.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, 0xff21121e);
      int knob = x + 2 + (int) ((getWidth() - 8) * value);
      g.fill(x + 2, y + getHeight() - 3, x + getWidth() - 2, y + getHeight() - 2, 0xff6c2848);
      g.fill(knob, y + getHeight() - 4, knob + 4, y + getHeight() - 1, 0xffb78b9d);
      g.drawCenteredString(font, getMessage(), x + getWidth() / 2, y + 5, OrganScreenStyle.TEXT);
    }

    @Override
    protected void updateMessage() {
      setMessage(
          Component.literal(
              String.format(
                  java.util.Locale.ROOT,
                  "%s offset: %+.2f blocks",
                  new String[] {"X", "Y", "Z"}[axis],
                  units() / (double) NameTagDisplayEntity.OFFSET_UNITS_PER_BLOCK)));
    }

    @Override
    protected void applyValue() {
      if (sliders[0] != null && sliders[1] != null && sliders[2] != null) {
        send();
      }
    }
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    var x = leftPos;
    var y = topPos;
    OrganScreenStyle.panel(g, x, y, imageWidth, imageHeight);
    g.renderItem(
        ComponentRegistry.NAME_TAG_DISPLAYER.get().asItem().getDefaultInstance(), x + 12, y + 8);
    g.drawString(font, "Named tag", x + 42, y + 48, OrganScreenStyle.MUTED, false);
    for (var slot : menu.slots) {
      OrganScreenStyle.slot(g, x + slot.x, y + slot.y);
    }
    g.drawCenteredString(
        font, "Local offsets · text stays upright", x + 114, y + 137, OrganScreenStyle.MUTED);
    OrganScreenStyle.divider(g, x + 12, y + 145, imageWidth - 24);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 34, 11, OrganScreenStyle.TEXT, false);
    g.drawString(
        font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, OrganScreenStyle.TEXT, false);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
