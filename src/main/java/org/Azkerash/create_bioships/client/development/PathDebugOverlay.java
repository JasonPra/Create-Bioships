package org.Azkerash.create_bioships.client.development;

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
import org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem;
import org.Azkerash.create_bioships.development.testing.network.*;

/** Draws the server's real route without running another path search on either side. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class PathDebugOverlay {
  private record Sample(PathDebugPayload path, long at) {}

  private static final Map<UUID, Sample> ROUTES = new HashMap<>();
  private static boolean enabled;
  private static net.minecraft.client.multiplayer.ClientLevel world;

  private PathDebugOverlay() {}

  public static boolean enabled() {
    return enabled;
  }

  public static int routeCount() {
    return ROUTES.size();
  }

  public static void toggle() {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null || !DevelopmentToolkitItem.allowed(mc.player)) return;
    enabled = !enabled;
    world = mc.level;
    ROUTES.clear();
    PacketDistributor.sendToServer(new PathDebugRequest(enabled));
    mc.gui.setOverlayMessage(
        Component.literal("Creature paths: " + (enabled ? "ON" : "OFF")), false);
  }

  public static void receive(PathDebugPayload p) {
    var mc = Minecraft.getInstance();
    if (!enabled || mc.level == null) return;
    if (p.points().isEmpty()) ROUTES.remove(p.body());
    else ROUTES.put(p.body(), new Sample(p, mc.level.getGameTime()));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (enabled && (mc.player == null || !DevelopmentToolkitItem.allowed(mc.player))) {
      enabled = false;
      ROUTES.clear();
      if (mc.getConnection() != null) PacketDistributor.sendToServer(new PathDebugRequest(false));
    }
    if (world != mc.level) {
      world = mc.level;
      ROUTES.clear();
      if (world == null) enabled = false;
      else if (enabled) PacketDistributor.sendToServer(new PathDebugRequest(true));
    }
    if (world != null) ROUTES.values().removeIf(s -> world.getGameTime() - s.at() > 40);
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
    for (var p : List.of(a, b))
      lines
          .addVertex(pose.last().pose(), (float) p.x, (float) p.y, (float) p.z)
          .setColor(r, g, blue, 1f)
          .setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (!enabled || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(DevelopmentDebugRenderType.LINES);
    var pose = e.getPoseStack();
    var camera = e.getCamera().getPosition();
    for (var sample : ROUTES.values()) {
      var p = sample.path();
      boolean water =
          p.mode() == org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder.Mode.WATER;
      for (int i = 0; i < p.points().size(); i++) {
        var at = p.points().get(i).add(0, .12, 0).subtract(camera);
        boolean passed = i < p.next();
        if (i > 0)
          line(
              pose,
              lines,
              p.points().get(i - 1).add(0, .12, 0).subtract(camera),
              at,
              passed ? .4f : .2f,
              passed ? .4f : water ? .5f : 1f,
              passed ? .4f : 1f);
        LevelRenderer.renderLineBox(
            pose,
            lines,
            new AABB(at, at).inflate(i == p.next() ? .16 : .07),
            i == p.next() ? 1f : .25f,
            passed ? .4f : 1f,
            passed ? .4f : i == p.next() ? .1f : .4f,
            1f);
      }
      var next = p.points().get(p.next()).add(0, .12, 0).subtract(camera);
      line(pose, lines, p.position().subtract(camera), next, 1f, .8f, .1f);
      var goal = p.points().getLast().add(0, .12, 0).subtract(camera);
      LevelRenderer.renderLineBox(pose, lines, new AABB(goal, goal).inflate(.25), .2f, 1f, .2f, 1f);
    }
    buffers.endBatch(DevelopmentDebugRenderType.LINES);
  }

  @SubscribeEvent
  public static void legend(RenderGuiEvent.Post e) {
    if (!enabled) return;
    var mc = Minecraft.getInstance();
    int y = mc.getWindow().getGuiScaledHeight() - 102;
    e.getGuiGraphics()
        .fill(5, y - 3, Math.min(mc.getWindow().getGuiScaledWidth() - 5, 460), y + 12, 0x99000000);
    e.getGuiGraphics()
        .drawString(
            mc.font,
            "Paths: cyan=land · blue=water · yellow=next · green=goal · "
                + ROUTES.size()
                + " active",
            8,
            y,
            0xffc5e8ea);
  }
}
