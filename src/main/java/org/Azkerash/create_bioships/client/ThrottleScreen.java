package org.Azkerash.create_bioships.client;

import org.Azkerash.create_bioships.BioshipNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ThrottleScreen extends Screen {
    private final BlockPos pos;
    private int setting;
    private AbstractSliderButton slider;
    public ThrottleScreen(BlockPos pos, int setting) { super(Component.translatable("block.create_bioships.living_throttle")); this.pos = pos; this.setting = Math.clamp(setting, 0, 20); }
    @Override protected void init() {
        slider = addRenderableWidget(new AbstractSliderButton(width / 2 - 110, height / 2 - 5, 220, 22, label(setting), setting / 20.0) {
            @Override protected void updateMessage() { setMessage(label((int)Math.round(value * 20))); }
            @Override protected void applyValue() { setting = (int)Math.round(value * 20); PacketDistributor.sendToServer(new BioshipNetwork.Throttle(pos, setting)); }
        });
        addRenderableWidget(Button.builder(Component.translatable("gui.create_bioships.neutral"), button -> {
            setting = 10; PacketDistributor.sendToServer(new BioshipNetwork.Throttle(pos, setting)); rebuildWidgets();
        }).bounds(width / 2 - 65, height / 2 + 36, 130, 20).build());
    }
    private static Component label(int setting) { return Component.translatable("gui.create_bioships.thrust", (setting - 10) * 10); }
    // This is an in-world instrument overlay; the default menu blur would blur its labels.
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(width / 2 - 130, height / 2 - 62, width / 2 + 130, height / 2 + 70, 0xef24152e);
        graphics.fill(width / 2 - 130, height / 2 - 62, width / 2 + 130, height / 2 - 60, 0xff62efbc);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xffa0ffe0);
        graphics.drawString(font, Component.translatable("gui.create_bioships.reverse"), width / 2 - 110, height / 2 - 23, 0xffcd88bf);
        graphics.drawString(font, Component.translatable("gui.create_bioships.ahead"), width / 2 + 65, height / 2 - 23, 0xff8deac2);
        graphics.drawCenteredString(font, Component.translatable("gui.create_bioships.slider_hint"), width / 2, height / 2 + 22, 0xffcab6d7);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void tick() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || !minecraft.level.getBlockState(pos).is(org.Azkerash.create_bioships.Create_bioships.THROTTLE.get())) onClose();
    }
}
