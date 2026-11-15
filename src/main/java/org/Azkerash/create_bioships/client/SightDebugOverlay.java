package org.Azkerash.create_bioships.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.*;

/** Diagnostic geometry comes from the server's actual vision code, not a client approximation. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class SightDebugOverlay {
  private static boolean enabled() {
    return Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes();
  }

  private static int nextRequest;
  private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

  private static boolean equipped() {
    var mc = Minecraft.getInstance();
    return mc.player != null
        && mc.level != null
        && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Create_bioships.BIOSHIP_GOGGLES.get());
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.level != lastLevel) {
      lastLevel = mc.level;
      nextRequest = 0;
    }
    if (!enabled() || !equipped()) {
      nextRequest = 0;
      if (mc.player != null) mc.player.getPersistentData().remove(VisionDebug.DATA);
      return;
    }
    if (mc.player.tickCount < nextRequest - 20) nextRequest = 0;
    if (enabled() && equipped() && mc.player.tickCount >= nextRequest) {
      nextRequest = mc.player.tickCount + 20;
      PacketDistributor.sendToServer(new BioshipNetwork.SightRequest());
    }
  }

  private static CompoundTag snapshot() {
    var mc = Minecraft.getInstance();
    if (!enabled() || !equipped() || mc.options.hideGui) return new CompoundTag();
    var data = mc.player.getPersistentData();
    if (mc.player.tickCount - data.getInt("SightReceived") > 45) return new CompoundTag();
    return data.getCompound(VisionDebug.DATA);
  }

  private static int color(String reason) {
    return switch (reason) {
      case "visible" -> 0xff70ff80;
      case "blocked" -> 0xffff5555;
      case "dark" -> 0xffffc04d;
      case "roof" -> 0xffee70ff;
      default -> 0xff9098ac;
    };
  }

  private static String label(String reason) {
    return switch (reason) {
      case "visible" -> "clear sight";
      case "blocked" -> "occluded";
      case "dark" -> "too dark";
      case "roof" -> "roof protection";
      case "range" -> "beyond range";
      case "unloaded" -> "unloaded terrain";
      default -> "outside cone";
    };
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (!enabled() || !equipped() || mc.options.hideGui || mc.screen != null) return;
    var data = snapshot();
    var views = data.getList("Views", Tag.TAG_COMPOUND);
    var g = event.getGuiGraphics();
    int y = drawText(g, "Sight debug · F3+B to toggle", 8, 0xff8fe5ef);
    if (views.isEmpty()) {
      drawText(g, "No active bioship within 64 blocks, or waiting for server.", y, 0xffeeeeee);
      return;
    }
    y =
        drawText(
            g,
            "Aim at a block · cyan: maximum 120° cones · colored: server sight tests",
            y,
            0xffeeeeee);
    y =
        drawText(
            g,
            "Core scouting: "
                + data.getString("ScoutStatus")
                + " · visibility does not guarantee collection",
            y,
            0xffeeeeee);
    y =
        drawText(
            g,
            "Sight cap: "
                + (int) views.getCompound(0).getDouble("Range")
                + " blocks · target: "
                + String.format(java.util.Locale.ROOT, "%.1f", data.getDouble("Distance"))
                + " blocks from core · "
                + (data.getDouble("Distance") <= 16 ? "inside" : "outside")
                + " 16-block world-source search",
            y,
            0xffeeeeee);
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (var entry : views)
      counts.merge(((CompoundTag) entry).getString("Reason"), 1, Integer::sum);
    for (var entry : counts.entrySet())
      y =
          drawText(
              g, entry.getValue() + " eye(s): " + label(entry.getKey()), y, color(entry.getKey()));
  }

  private static int drawText(
      net.minecraft.client.gui.GuiGraphics g, String text, int y, int color) {
    var mc = Minecraft.getInstance();
    for (var line :
        mc.font.split(
            Component.literal(text), Math.max(40, mc.getWindow().getGuiScaledWidth() - 16))) {
      if (y > mc.getWindow().getGuiScaledHeight() - 12) break;
      g.drawString(mc.font, line, 8, y, color, true);
      y += 11;
    }
    return y;
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var data = snapshot();
    var views = data.getList("Views", Tag.TAG_COMPOUND);
    if (views.isEmpty()) return;
    var mc = Minecraft.getInstance();
    var pose = event.getPoseStack();
    pose.pushPose();
    var camera = event.getCamera().getPosition();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var buffer = buffers.getBuffer(RenderType.lines());
    var target = VisionDebug.vector(data.getCompound("Target"));
    for (var entry : views) {
      var view = (CompoundTag) entry;
      var origin = VisionDebug.vector(view.getCompound("Origin"));
      var forward = VisionDebug.vector(view.getCompound("Forward")).normalize();
      var right =
          forward
              .cross(Math.abs(forward.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0))
              .normalize();
      var up = right.cross(forward).normalize();
      double range = view.getDouble("Range");
      Vec3 previous = null, first = null;
      for (int i = 0; i < 24; i++) {
        double angle = i * Math.PI / 12;
        var edge =
            origin
                .add(forward.scale(range * .5))
                .add(right.scale(Math.cos(angle) * range * Math.sqrt(3) / 2))
                .add(up.scale(Math.sin(angle) * range * Math.sqrt(3) / 2));
        if (previous != null) line(pose.last(), buffer, previous, edge, 0x6670dce5);
        if (i % 3 == 0) line(pose.last(), buffer, origin, edge, 0x6670dce5);
        if (first == null) first = edge;
        previous = edge;
      }
      line(pose.last(), buffer, previous, first, 0x6670dce5);
      if (!view.getString("Reason").equals("cone"))
        line(pose.last(), buffer, origin, target, color(view.getString("Reason")));
    }
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private static void line(
      com.mojang.blaze3d.vertex.PoseStack.Pose pose,
      VertexConsumer buffer,
      Vec3 a,
      Vec3 b,
      int color) {
    var normal = b.subtract(a).normalize();
    buffer
        .addVertex(pose, (float) a.x, (float) a.y, (float) a.z)
        .setColor(color)
        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    buffer
        .addVertex(pose, (float) b.x, (float) b.y, (float) b.z)
        .setColor(color)
        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
  }
}
