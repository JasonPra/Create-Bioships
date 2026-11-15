package org.Azkerash.create_bioships.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.Azkerash.create_bioships.*;

@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class SymbioticTooltip {
  @SubscribeEvent
  public static void render(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.options.hideGui
        || mc.screen != null
        || mc.player == null
        || mc.level == null
        || !mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Create_bioships.BIOSHIP_GOGGLES.get())
        || !(mc.hitResult instanceof BlockHitResult hit)) return;
    var pos = hit.getBlockPos();
    var state = mc.level.getBlockState(pos);
    List<Component> details = new ArrayList<>();
    if (mc.level.getBlockEntity(pos) instanceof LivingCoreBlockEntity core)
      details.addAll(core.goggleTooltip());
    else if (state.is(Create_bioships.INTESTINE.get())) {
      details.add(Component.literal("Fleshy intestine"));
      var connections = new ArrayList<String>();
      IntestineBlock.CONNECTIONS.forEach(
          (d, p) -> {
            if (state.getValue(p)) connections.add(d.getName());
          });
      details.add(
          Component.literal(
              connections.isEmpty()
                  ? "No compatible connections"
                  : "Connected: " + String.join(", ", connections)));
      details.add(
          Component.literal(
              state.getValue(IntestineBlock.ENCASED)
                  ? "Encased in living flesh"
                  : "Use living flesh to encase this tube."));
    } else if (state.is(Create_bioships.BLUEPRINT_PANEL.get())) {
      details.add(Component.literal("Blueprint nerve panel"));
      details.add(
          Component.literal(
              "Right-click to design rooms, corridors, walls and doors. Only the creator can"
                  + " approve changes."));
    } else if (state.is(Create_bioships.FEEDING_MOUTH.get())) {
      details.add(Component.literal("Feeding maw"));
      details.add(
          Component.literal(
              "Feed meat or amalgam here. An intact intestine must connect this mouth to the"
                  + " stomach."));
    } else if (state.is(Create_bioships.STOMACH.get())) {
      details.add(Component.literal("Bioship stomach"));
      details.add(
          Component.literal(
              "Right-click to inspect acid, digestion and building reserves. Products are retained"
                  + " for growth and repairs."));
    }
    if (details.isEmpty()) return;
    int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
    int maxWidth = Math.min(230, Math.max(80, w / 2 - 34));
    List<FormattedCharSequence> lines = new ArrayList<>();
    for (var line : details) lines.addAll(mc.font.split(line, maxWidth));
    int textWidth = lines.stream().mapToInt(mc.font::width).max().orElse(0),
        height = lines.size() * 10;
    // Reserve a margin on all four edges, including at high GUI scales.
    float scale = Math.min(1f, (h - 20f) / Math.max(1, height + 12));
    var g = event.getGuiGraphics();
    g.pose().pushPose();
    g.pose().scale(scale, scale, 1);
    int x = Math.max(8, Math.min((int) ((w / 2 + 18) / scale), (int) (w / scale) - textWidth - 12));
    int y = Math.max(8, Math.min((int) ((h / 2 - 25) / scale), (int) (h / scale) - height - 12));
    g.fill(x - 6, y - 6, x + textWidth + 6, y + height + 5, 0xdd241c25);
    g.renderOutline(x - 7, y - 7, textWidth + 14, height + 13, 0xff9a6a83);
    for (int i = 0; i < lines.size(); i++)
      g.drawString(mc.font, lines.get(i), x, y + i * 10, i == 0 ? 0xffd7e8ae : 0xffe4d6d0, false);
    g.pose().popPose();
  }
}
