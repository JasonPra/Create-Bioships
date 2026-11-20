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
import org.azkerash.createbioships.development.testing.DevelopmentToolkitItem;
import org.azkerash.createbioships.development.testing.network.*;
import org.azkerash.createbioships.equipment.DiagnosticAccess;

/** Client-only arrows follow the current render pose of authorized creature roots. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class FacingDebugOverlay {
  // Expire stale client snapshots even when an unsubscribe/removal packet is missed.
  private static final int SNAPSHOT_EXPIRY_TICKS = 40;

  private record Sample(FacingDebugPayload path, long at) {}

  private static final Map<UUID, Sample> ROUTES = new HashMap<>();
  private static boolean enabled;
  private static net.minecraft.client.multiplayer.ClientLevel world;

  private FacingDebugOverlay() {}

  public static boolean enabled() {
    return enabled;
  }

  public static int markerCount() {
    return ROUTES.size();
  }

  public static void toggle() {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || !DiagnosticAccess.allowed(mc.player)) {
      return;
    }
    enabled = !enabled;
    world = mc.level;
    ROUTES.clear();
    PacketDistributor.sendToServer(new FacingDebugRequest(enabled));
    mc.gui.setOverlayMessage(
        Component.literal("Creature facing: " + (enabled ? "ON" : "OFF")), false);
  }

  public static void receive(FacingDebugPayload p) {
    var mc = Minecraft.getInstance();
    if (!enabled || mc.level == null) {
      return;
    }
    if (!p.visible()) {
      ROUTES.remove(p.body());
    } else {
      ROUTES.put(p.body(), new Sample(p, mc.level.getGameTime()));
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (enabled && (mc.player == null || !DiagnosticAccess.allowed(mc.player))) {
      enabled = false;
      ROUTES.clear();
      if (mc.getConnection() != null) {
        PacketDistributor.sendToServer(new FacingDebugRequest(false));
      }
    }
    if (world != mc.level) {
      world = mc.level;
      ROUTES.clear();
      if (world == null) {
        enabled = false;
      } else if (enabled) {
        PacketDistributor.sendToServer(new FacingDebugRequest(true));
      }
    }
    if (world != null) {
      ROUTES.values().removeIf(s -> world.getGameTime() - s.at() > SNAPSHOT_EXPIRY_TICKS);
    }
  }

  private static void line(
      com.mojang.blaze3d.vertex.PoseStack pose,
      com.mojang.blaze3d.vertex.VertexConsumer lines,
      Vec3 a,
      Vec3 b,
      float r,
      float g,
      float blue) {
    var n = b.subtract(a).normalize();
    for (var p : List.of(a, b)) {
      lines
          .addVertex(pose.last().pose(), (float) p.x, (float) p.y, (float) p.z)
          .setColor(r, g, blue, 1f)
          .setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (!enabled || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
      return;
    }
    var mc = Minecraft.getInstance();
    if (mc.level == null) {
      return;
    }
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(DevelopmentDebugRenderType.LINES);
    var pose = e.getPoseStack();
    var camera = e.getCamera().getPosition();
    for (var sample : ROUTES.values()) {
      var p = sample.path();
      var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level);
      if (container == null) {
        continue;
      }
      if (!(container.getSubLevel(p.body())
          instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel body)) {
        continue;
      }
      var direction = Vec3.atLowerCornerOf(p.front().getNormal());
      var right = direction.cross(new Vec3(0, 1, 0));
      var tip = p.origin().add(direction.scale(2));
      var back = tip.subtract(direction.scale(.45));
      var base = body.renderPose().transformPosition(p.origin()).subtract(camera);
      var end = body.renderPose().transformPosition(tip).subtract(camera);
      line(pose, lines, base, end, .15f, 1f, .9f);
      line(
          pose,
          lines,
          end,
          body.renderPose().transformPosition(back.add(right.scale(.3))).subtract(camera),
          .15f,
          1f,
          .9f);
      line(
          pose,
          lines,
          end,
          body.renderPose().transformPosition(back.subtract(right.scale(.3))).subtract(camera),
          .15f,
          1f,
          .9f);
      LevelRenderer.renderLineBox(
          pose, lines, new AABB(base, base).inflate(.08), .15f, 1f, .9f, 1f);
    }
    buffers.endBatch(DevelopmentDebugRenderType.LINES);
  }

  @SubscribeEvent
  public static void legend(RenderGuiEvent.Post e) {
    if (Minecraft.getInstance().screen
        instanceof org.azkerash.createbioships.client.equipment.SomaticResonatorScreen) {
      return;
    }
    if (!enabled) {
      return;
    }
    var mc = Minecraft.getInstance();
    int y = mc.getWindow().getGuiScaledHeight() - 102;
    e.getGuiGraphics()
        .fill(5, y - 3, Math.min(mc.getWindow().getGuiScaledWidth() - 5, 460), y + 12, 0x99000000);
    e.getGuiGraphics()
        .drawString(
            mc.font,
            DevelopmentToolkitItem.allowed(mc.player)
                ? "Facing: cyan arrow = front · Shift-use toolkit to set front"
                : "Facing: cyan arrow marks the creature’s front",
            8,
            y,
            0xffc5e8ea);
  }
}
