package org.azkerash.createbioships.client.components;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.components.logic.brain.*;
import org.azkerash.createbioships.core.brain.hunting.HuntingCardData;

/** Fixed-function editor: adding/removing species is the only editable hunting setting. */
public final class HuntingCardScreen extends AbstractContainerScreen<HuntingCardMenu> {
  private static final int ROWS = 5;
  private final List<ResourceLocation> prey = new ArrayList<>();
  private EditBox input;
  private int page;
  private String status = "";

  public HuntingCardScreen(HuntingCardMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    prey.addAll(HuntingCardData.parse(menu.getInitialWhitelist()));
    imageWidth = 300;
    imageHeight = 250;
  }

  @Override
  protected void init() {
    super.init();
    input =
        addRenderableWidget(
            new EditBox(
                font,
                leftPos + 16,
                topPos + 49,
                202,
                20,
                Component.literal("Entity type, for example minecraft:pig")));
    input.setMaxLength(128);
    addRenderableWidget(
        new OrganButton(leftPos + 224, topPos + 49, 60, 20, "Add", b -> add(input.getValue())));
    addRenderableWidget(
        new OrganButton(
            leftPos + 16,
            topPos + 74,
            166,
            18,
            "Add looked-at mob",
            b -> {
              var entity = minecraft.crosshairPickEntity;
              if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
              } else {
                status = "Look at a mob before opening this card.";
              }
            }));
    for (int i = 0; i < ROWS; i++) {
      final int row = i;
      addRenderableWidget(
          new OrganButton(
              leftPos + 260,
              topPos + 101 + i * 18,
              24,
              16,
              "−",
              b -> {
                int index = page * ROWS + row;
                if (index < prey.size()) {
                  prey.remove(index);
                  page = Math.min(page, Math.max(0, (prey.size() - 1) / ROWS));
                }
              }));
    }
    addRenderableWidget(
        new OrganButton(
            leftPos + 16, topPos + 195, 24, 18, "<", b -> page = Math.max(0, page - 1)));
    addRenderableWidget(
        new OrganButton(
            leftPos + 260,
            topPos + 195,
            24,
            18,
            ">",
            b -> page = Math.min(Math.max(0, (prey.size() - 1) / ROWS), page + 1)));
    addRenderableWidget(
        new OrganButton(
            leftPos + 16,
            topPos + 222,
            128,
            20,
            "Save whitelist",
            b ->
                PacketDistributor.sendToServer(
                    new HuntingCardNetwork.Save(
                        menu.containerId,
                        String.join(",", prey.stream().map(Object::toString).toList())))));
    addRenderableWidget(
        new OrganButton(leftPos + 156, topPos + 222, 128, 20, "Close", b -> onClose()));
    setInitialFocus(input);
  }

  private void add(String text) {
    try {
      var updated = new LinkedHashSet<>(prey);
      updated.addAll(HuntingCardData.parse(text));
      if (updated.size() > HuntingCardData.MAX_TYPES) {
        throw new IllegalArgumentException(
            "Choose at most " + HuntingCardData.MAX_TYPES + " types.");
      }
      prey.clear();
      prey.addAll(updated);
      input.setValue("");
      status = "";
    } catch (IllegalArgumentException invalid) {
      status = invalid.getMessage();
    }
  }

  public static void receive(HuntingCardNetwork.Result result) {
    if (Minecraft.getInstance().screen instanceof HuntingCardScreen screen
        && screen.menu.containerId == result.menu()) {
      screen.status = result.error().isEmpty() ? "Whitelist saved on this card." : result.error();
    }
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (input.isFocused() && key == 258 && !input.getValue().isBlank()) {
      String prefix = input.getValue();
      BuiltInRegistries.ENTITY_TYPE.keySet().stream()
          .map(Object::toString)
          .sorted()
          .filter(s -> s.startsWith(prefix) || s.startsWith("minecraft:" + prefix))
          .findFirst()
          .ifPresent(input::setValue);
      return true;
    }
    if (input.isFocused() && (key == 257 || key == 335)) {
      add(input.getValue());
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    OrganScreenStyle.panel(g, leftPos, topPos, imageWidth, imageHeight);
    g.drawCenteredString(font, "Prey whitelist", leftPos + 150, topPos + 12, 0xfff0d2c3);
    g.drawString(
        font,
        "Only listed species can be hunted.",
        leftPos + 16,
        topPos + 29,
        OrganScreenStyle.MUTED,
        false);
    for (int i = 0; i < ROWS; i++) {
      int index = page * ROWS + i;
      if (index < prey.size()) {
        g.drawString(
            font,
            font.plainSubstrByWidth(prey.get(index).toString(), 235),
            leftPos + 16,
            topPos + 105 + i * 18,
            0xfff0d2c3,
            false);
      }
    }
    if (prey.isEmpty()) {
      g.drawString(
          font,
          "Empty whitelist: hunting is disabled.",
          leftPos + 16,
          topPos + 108,
          OrganScreenStyle.MUTED,
          false);
    }
    g.drawCenteredString(
        font,
        (page + 1)
            + " / "
            + Math.max(1, (prey.size() + ROWS - 1) / ROWS)
            + " · "
            + prey.size()
            + " types",
        leftPos + 150,
        topPos + 199,
        OrganScreenStyle.MUTED);
    if (!status.isEmpty()) {
      g.drawString(
          font,
          font.plainSubstrByWidth(status, 268),
          leftPos + 16,
          topPos + 211,
          0xffd7aa8f,
          false);
    }
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {}

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    if (!status.isEmpty() && my >= topPos + 209 && my < topPos + 222) {
      g.renderTooltip(font, Component.literal(status), mx, my);
    }
  }
}
