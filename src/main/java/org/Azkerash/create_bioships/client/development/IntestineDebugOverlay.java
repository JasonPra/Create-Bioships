package org.Azkerash.create_bioships.client.development;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.development.testing.network.*;

/** The toolkit draws the authoritative server graph in the grid's interpolated coordinate frame. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class IntestineDebugOverlay {
  private static boolean enabled;
  private static net.minecraft.client.multiplayer.ClientLevel world;
  private static final Map<UUID, DigestiveDebugPayload> GRAPHS = new HashMap<>();

  private IntestineDebugOverlay() {}

  public static boolean enabled() {
    return enabled;
  }

  public static int graphCount() {
    return GRAPHS.size();
  }

  public static void toggle() {
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || !org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem.allowed(
            mc.player)) return;
    enabled = !enabled;
    GRAPHS.clear();
    world = mc.level;
    PacketDistributor.sendToServer(new DigestiveDebugRequest(enabled));
    mc.gui.setOverlayMessage(
        Component.literal("Intestine graph: " + (enabled ? "ON" : "OFF")), false);
  }

  public static void toggleOff() {
    if (!enabled) return;
    enabled = false;
    GRAPHS.clear();
    if (Minecraft.getInstance().getConnection() != null)
      PacketDistributor.sendToServer(new DigestiveDebugRequest(false));
  }

  public static void receive(DigestiveDebugPayload payload) {
    if (!enabled) return;
    if (payload.nodes().isEmpty()) GRAPHS.remove(payload.body());
    else GRAPHS.put(payload.body(), payload);
  }

  @SubscribeEvent
  public static void changedLevel(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (enabled
        && mc.player != null
        && !org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem.allowed(
            mc.player)) {
      toggleOff();
    }
    if (world == mc.level) return;
    world = mc.level;
    GRAPHS.clear();
    if (mc.level == null) enabled = false;
    else if (enabled) PacketDistributor.sendToServer(new DigestiveDebugRequest(true));
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
      return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    var container = SubLevelContainer.getContainer(mc.level);
    if (container == null) return;
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(DevelopmentDebugRenderType.LINES);
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    for (var graph : GRAPHS.values()) {
      var body = container.getSubLevel(graph.body());
      if (!(body instanceof ClientSubLevel client) || body.isRemoved()) continue;
      var transform = client.renderPose();
      var origin = body.getPlot().getCenterBlock();
      var nodes = new HashMap<BlockPos, Integer>();
      for (var node : graph.nodes()) nodes.put(node.pos(), node.kind());
      pose.pushPose();
      pose.translate(
          transform.position().x() - camera.x,
          transform.position().y() - camera.y,
          transform.position().z() - camera.z);
      pose.mulPose(new org.joml.Quaternionf(transform.orientation()));
      pose.scale(
          (float) transform.scale().x(),
          (float) transform.scale().y(),
          (float) transform.scale().z());
      pose.translate(
          origin.getX() - transform.rotationPoint().x(),
          origin.getY() - transform.rotationPoint().y(),
          origin.getZ() - transform.rotationPoint().z());
      for (var node : graph.nodes()) {
        var point = Vec3.atCenterOf(node.pos()).subtract(Vec3.atLowerCornerOf(origin));
        int degree = 0;
        Direction neighbor = null;
        for (var face : Direction.values()) {
          var next = node.pos().relative(face);
          if (!org.Azkerash.create_bioships.core.digestion.SubLevelDigestion.connected(
              node.kind(), nodes.getOrDefault(next, 0), face)) continue;
          degree++;
          neighbor = face;
          var end = point.relative(face, 1);
          if (node.pos().compareTo(next) < 0)
            LevelRenderer.renderLineBox(
                pose, lines, new AABB(point, end).inflate(.008), .25f, .75f, 1f, 1f);
          if (node.kind() == 1 && nodes.get(next) != 1)
            LevelRenderer.renderLineBox(
                pose,
                lines,
                new AABB(point.relative(face, .5), point.relative(face, .5)).inflate(.10),
                .2f,
                1f,
                .35f,
                1f);
        }
        if (node.kind() != 1) {
          float red = node.kind() == 2 ? 1f : node.kind() == 3 ? .8f : .2f;
          float green = node.kind() == 2 ? .7f : node.kind() == 3 ? .3f : 1f;
          LevelRenderer.renderLineBox(
              pose,
              lines,
              new AABB(point, point).inflate(node.slots() > 0 ? .48 : .18),
              red,
              green,
              1f,
              1f);
        } else if (degree <= 1) {
          var endpoint = degree == 0 ? point : point.relative(neighbor.getOpposite(), .5);
          LevelRenderer.renderLineBox(
              pose, lines, new AABB(endpoint, endpoint).inflate(.11), 1f, .15f, .15f, 1f);
          if (degree == 1)
            LevelRenderer.renderLineBox(
                pose, lines, new AABB(point, endpoint).inflate(.008), 1f, .3f, .3f, 1f);
        }
      }
      pose.popPose();
      for (var node : graph.nodes())
        if (node.slots() > 0) {
          var at =
              transform
                  .transformPosition(Vec3.atCenterOf(node.pos()))
                  .add(0, .8, 0)
                  .subtract(camera);
          String name = node.kind() == 4 ? "Storage sac" : node.kind() == 3 ? "Stomach" : "Mouth";
          String text =
              name
                  + " · "
                  + node.used()
                  + "/"
                  + node.slots()
                  + " slots · "
                  + node.items()
                  + " items";
          pose.pushPose();
          pose.translate(at.x, at.y, at.z);
          pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
          pose.scale(-.02f, -.02f, .02f);
          mc.font.drawInBatch(
              text,
              -mc.font.width(text) / 2f,
              0,
              0xffbbffff,
              false,
              pose.last().pose(),
              buffers,
              net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
              0x66000000,
              LightTexture.FULL_BRIGHT);
          pose.popPose();
        }
    }
    buffers.endBatch(DevelopmentDebugRenderType.LINES);
  }

  @SubscribeEvent
  public static void legend(RenderGuiEvent.Post event) {
    if (!enabled) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    var g = event.getGuiGraphics();
    int y = mc.getWindow().getGuiScaledHeight() - 57;
    String label =
        "Toolkit: intestine graph · red=open · green=connected · " + GRAPHS.size() + " grids";
    g.fill(5, y - 3, Math.min(mc.getWindow().getGuiScaledWidth() - 5, 420), y + 24, 0x99000000);
    g.drawString(mc.font, label, 8, y, 0xffc5e8ea);
    g.drawString(mc.font, "Pink: mouth · purple: stomach · cyan: sac", 8, y + 12, 0xffc5e8ea);
  }
}
