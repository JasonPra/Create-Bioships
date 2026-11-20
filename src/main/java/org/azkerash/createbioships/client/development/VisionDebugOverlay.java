package org.azkerash.createbioships.client.development;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.azkerash.createbioships.development.testing.network.*;

/**
 * Client-local visualization of server-authorized snapshots; toggling this never changes detection.
 */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class VisionDebugOverlay {
  // Expire stale client snapshots even when an unsubscribe/removal packet is missed.
  private static final int SNAPSHOT_EXPIRY_TICKS = 40;

  private record Snapshot(VisionDebugPayload payload, long at) {}

  private static boolean enabled;
  private static net.minecraft.client.multiplayer.ClientLevel world;
  private static final Map<UUID, Snapshot> SAMPLES = new HashMap<>();

  private VisionDebugOverlay() {}

  public static boolean enabled() {
    return enabled;
  }

  public static int gridCount() {
    return SAMPLES.size();
  }

  public static void toggle() {
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || !org.azkerash.createbioships.development.testing.DevelopmentToolkitItem.allowed(
            mc.player)) {
      return;
    }
    enabled = !enabled;
    SAMPLES.clear();
    world = mc.level;
    PacketDistributor.sendToServer(new VisionDebugRequest(enabled));
    mc.gui.setOverlayMessage(Component.literal("Vision rays: " + (enabled ? "ON" : "OFF")), false);
  }

  public static void toggleOff() {
    if (!enabled) {
      return;
    }
    enabled = false;
    SAMPLES.clear();
    if (Minecraft.getInstance().getConnection() != null) {
      PacketDistributor.sendToServer(new VisionDebugRequest(false));
    }
  }

  public static void receive(VisionDebugPayload payload) {
    var mc = Minecraft.getInstance();
    if (!enabled || mc.level == null) {
      return;
    }
    if (payload.rays().isEmpty()) {
      SAMPLES.remove(payload.body());
    } else {
      SAMPLES.put(payload.body(), new Snapshot(payload, mc.level.getGameTime()));
    }
  }

  @SubscribeEvent
  public static void changedLevel(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (enabled
        && mc.player != null
        && !org.azkerash.createbioships.development.testing.DevelopmentToolkitItem.allowed(
            mc.player)) {
      toggleOff();
    }
    if (world != mc.level) {
      world = mc.level;
      SAMPLES.clear();
      if (world == null) {
        enabled = false;
      } else if (enabled) {
        PacketDistributor.sendToServer(new VisionDebugRequest(true));
      }
    }
    if (world != null) {
      SAMPLES.values().removeIf(s -> world.getGameTime() - s.at > SNAPSHOT_EXPIRY_TICKS);
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
      return;
    }
    var mc = Minecraft.getInstance();
    if (mc.level == null) {
      return;
    }
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(DevelopmentDebugRenderType.LINES);
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    for (var snapshot : SAMPLES.values()) {
      for (var ray : snapshot.payload.rays()) {
        var from = ray.from().subtract(camera);
        var to = ray.to().subtract(camera);
        float r = ray.blocked() ? 1f : ray.unloaded() ? 1f : .2f;
        float g = ray.blocked() ? .25f : ray.unloaded() ? .8f : .9f;
        float b = ray.blocked() ? .2f : ray.unloaded() ? .1f : 1f;
        var normal = to.subtract(from).normalize();
        lines
            .addVertex(pose.last().pose(), (float) from.x, (float) from.y, (float) from.z)
            .setColor(r, g, b, .65f)
            .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        lines
            .addVertex(pose.last().pose(), (float) to.x, (float) to.y, (float) to.z)
            .setColor(r, g, b, .65f)
            .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        LevelRenderer.renderLineBox(pose, lines, new AABB(to, to).inflate(.045), r, g, b, 1);
      }
    }
    for (var snapshot : SAMPLES.values()) {
      for (var target : snapshot.payload.targets()) {
        var at = target.subtract(camera);
        LevelRenderer.renderLineBox(pose, lines, new AABB(at, at).inflate(.2), .25f, 1f, .35f, 1f);
      }
    }
    buffers.endBatch(DevelopmentDebugRenderType.LINES);
  }

  @SubscribeEvent
  public static void legend(RenderGuiEvent.Post event) {
    if (!enabled) {
      return;
    }
    var mc = Minecraft.getInstance();
    if (mc.level == null) {
      return;
    }
    int y = mc.getWindow().getGuiScaledHeight() - 84;
    var g = event.getGuiGraphics();
    g.fill(5, y - 3, Math.min(mc.getWindow().getGuiScaledWidth() - 5, 450), y + 12, 0x99000000);
    g.drawString(
        mc.font,
        "Toolkit: vision · cyan=clear · red=blocked · yellow=unloaded · green=seen · "
            + SAMPLES.size()
            + " grids",
        8,
        y,
        0xffc5e8ea);
  }
}
