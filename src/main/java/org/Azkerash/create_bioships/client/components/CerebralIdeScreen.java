package org.Azkerash.create_bioships.client.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.components.logic.brain.*;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

/** Source editing stays client-side; only explicit saves alter a physical card on the server. */
public final class CerebralIdeScreen extends AbstractContainerScreen<CardWorkspaceMenu> {
  private MultiLineEditBox editor;
  private String text, status = "Draft editor · Save explicitly to write the card.";
  private boolean dirty, replaying;
  private final java.util.Deque<String> undo = new java.util.ArrayDeque<>(),
      redo = new java.util.ArrayDeque<>();

  public CerebralIdeScreen(CardWorkspaceMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    text = menu.initialSource;
  }

  @Override
  protected void init() {
    imageWidth = Math.min(760, width - 20);
    imageHeight = height - 24;
    super.init();
    editor =
        addRenderableWidget(
            new MultiLineEditBox(
                font,
                leftPos + 12,
                topPos + 39,
                imageWidth - 24,
                imageHeight - 110,
                Component.literal("C# source"),
                Component.literal("C# program editor")));
    editor.setCharacterLimit(ProgramCardData.MAX_SOURCE);
    editor.setValue(text);
    editor.setValueListener(
        value -> {
          if (!replaying && !text.equals(value)) {
            undo.push(text);
            while (undo.size() > 64) undo.removeLast();
            redo.clear();
          }
          text = value;
          dirty = true;
        });
    addRenderableWidget(
        Button.builder(Component.literal("Save draft"), b -> save())
            .bounds(leftPos + 12, topPos + imageHeight - 32, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(leftPos + imageWidth - 72, topPos + imageHeight - 32, 60, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Compile & write"), b -> compile())
            .bounds(leftPos + 118, topPos + imageHeight - 32, 106, 20)
            .build());
    setInitialFocus(editor);
  }

  private void save() {
    PacketDistributor.sendToServer(
        new CardWorkspaceNetwork.Save(menu.containerId, menu.identity, editor.getValue()));
    status = "Saving…";
  }

  private void compile() {
    PacketDistributor.sendToServer(
        new CardWorkspaceNetwork.Write(menu.containerId, menu.identity, editor.getValue()));
    status = "Compiling on the server…";
  }

  public static void receive(CardWorkspaceNetwork.Result result) {
    if (Minecraft.getInstance().screen instanceof CerebralIdeScreen screen
        && screen.menu.containerId == result.menu()
        && screen.menu.identity.equals(result.identity())) {
      screen.status = result.message();
      if (result.saved()
          && result.fingerprint().equals(ProgramCardData.fingerprint(screen.editor.getValue())))
        screen.dirty = false;
    }
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (hasControlDown()
        && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_Z || key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y)) {
      boolean forward = key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y || hasShiftDown();
      var from = forward ? redo : undo;
      var to = forward ? undo : redo;
      if (!from.isEmpty()) {
        to.push(editor.getValue());
        replaying = true;
        editor.setValue(from.pop());
        replaying = false;
        dirty = true;
      }
      return true;
    }
    if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
      compile();
      return true;
    }
    if (hasControlDown() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
      save();
      return true;
    }
    if (editor.isFocused() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && !hasShiftDown()) {
      for (int i = 0; i < 4; i++) editor.charTyped(' ', 0);
      return true;
    }
    if (editor.isFocused() && minecraft.options.keyInventory.matches(key, scan))
      return editor.keyPressed(key, scan, modifiers);
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    OrganScreenStyle.panel(g, leftPos, topPos, imageWidth, imageHeight);
    g.drawString(
        font,
        "Source.cs" + (dirty ? " *" : ""),
        leftPos + 12,
        topPos + 27,
        OrganScreenStyle.MUTED,
        false);
    g.fill(
        leftPos + 10,
        topPos + 37,
        leftPos + imageWidth - 10,
        topPos + imageHeight - 69,
        0xff100e16);
    g.drawString(
        font,
        font.plainSubstrByWidth(status, imageWidth - 24),
        leftPos + 12,
        topPos + imageHeight - 61,
        OrganScreenStyle.TEXT,
        false);
    g.drawString(
        font,
        "Ctrl+S saves · Ctrl+Enter compiles",
        leftPos + 12,
        topPos + imageHeight - 48,
        OrganScreenStyle.MUTED,
        false);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 12, 10, OrganScreenStyle.TEXT, false);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
  }
}
