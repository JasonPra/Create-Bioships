package org.Azkerash.create_bioships.client.development;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem;
import org.Azkerash.create_bioships.development.testing.network.*;
import org.joml.Matrix4f;

/** Opt-in, read-only holograms of the same server values used by creature metabolism. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class CreatureStatsOverlay {
  // Expire stale client snapshots even when an unsubscribe/removal packet is missed.
  private static final int SNAPSHOT_EXPIRY_TICKS = 30;

  private record Snapshot(CreatureStatsPayload data, long at) {}

  private record Text(String value, Matrix4f pose, int y, int color) {}

  private static final Map<UUID, Snapshot> STATS = new HashMap<>();
  private static boolean enabled;
  private static Object world;

  private CreatureStatsOverlay() {}

  public static boolean enabled() {
    return enabled;
  }

  public static void toggle() {
    enabled = !enabled;
    STATS.clear();
    PacketDistributor.sendToServer(new CreatureStatsRequest(enabled));
  }

  public static void receive(CreatureStatsPayload p) {
    var mc = Minecraft.getInstance();
    if (!enabled || mc.level == null) {
      return;
    }
    if (world != mc.level) {
      STATS.clear();
      world = mc.level;
    }
    if (p.rows().isEmpty()) {
      STATS.remove(p.body());
      return;
    }
    STATS.put(p.body(), new Snapshot(p, mc.level.getGameTime()));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (enabled && mc.player != null && !DevelopmentToolkitItem.allowed(mc.player)) {
      toggle();
    }
    if (world == mc.level) {
      return;
    }
    world = mc.level;
    STATS.clear();
    if (mc.level == null) {
      enabled = false;
    } else if (enabled) {
      PacketDistributor.sendToServer(new CreatureStatsRequest(true));
    }
  }

  private static void rect(
      VertexConsumer v, Matrix4f p, float x0, float y0, float x1, float y1, int color) {
    v.addVertex(p, x0, y0, 0).setColor(color);
    v.addVertex(p, x0, y1, 0).setColor(color);
    v.addVertex(p, x1, y1, 0).setColor(color);
    v.addVertex(p, x1, y0, 0).setColor(color);
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
    STATS.values().removeIf(s -> mc.level.getGameTime() - s.at() > SNAPSHOT_EXPIRY_TICKS);
    var buffers = mc.renderBuffers().bufferSource();
    var type = RenderType.gui();
    var quads = buffers.getBuffer(type);
    var labels = new ArrayList<Text>();
    var camera = e.getCamera().getPosition();
    for (var snapshot : STATS.values()) {
      var data = snapshot.data();
      var at = data.at().subtract(camera);
      PoseStack pose = e.getPoseStack();
      pose.pushPose();
      pose.translate(at.x, at.y, at.z);
      pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
      pose.scale(.018f, -.018f, .018f);
      pose.translate(-157, -(data.rows().size() * 18 + 26), 0);
      var matrix = new Matrix4f(pose.last().pose());
      rect(quads, matrix, 0, 0, 314, data.rows().size() * 18 + 26, 0xdc101923);
      rect(quads, matrix, 0, 0, 314, 2, 0xff86c5cc);
      labels.add(
          new Text("CREATURE " + data.body().toString().substring(0, 8), matrix, 7, 0xffd6f1ed));
      int y = 24;
      for (var row : data.rows()) {
        if (row.fraction() >= 0) {
          rect(quads, matrix, 6, y + 10, 308, y + 13, 0xff293342);
          rect(quads, matrix, 6, y + 10, (float) (6 + 302 * row.fraction()), y + 13, row.color());
        }
        labels.add(new Text(row.text(), matrix, y, row.color()));
        y += 18;
      }
      pose.popPose();
    }
    buffers.endBatch(type);
    for (var text : labels) {
      mc.font.drawInBatch(
          text.value(),
          6,
          text.y(),
          text.color(),
          false,
          text.pose(),
          buffers,
          net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
          0,
          LightTexture.FULL_BRIGHT);
    }
    buffers.endBatch();
  }
}
