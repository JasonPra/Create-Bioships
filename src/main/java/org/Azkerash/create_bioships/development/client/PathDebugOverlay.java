package org.Azkerash.create_bioships.development.client;

import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.network.BioshipNetwork;
import org.Azkerash.create_bioships.network.VisionDebug;
import org.Azkerash.create_bioships.network.NavigationDebug;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.*;

/**
 * F3+P is independent of hitboxes, sight cones and equipment. Snapshots are server observations.
 */
@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class PathDebugOverlay {
  private static boolean enabled;
  private static int nextRequest;
  private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

  public static void toggle() {
    var mc = Minecraft.getInstance();
    enabled = !enabled;
    lastLevel = mc.level;
    nextRequest = 0;
    if (mc.player != null) {
      mc.player.getPersistentData().remove(NavigationDebug.DATA);
      mc.player.displayClientMessage(
          Component.literal("Bioship path debug: " + (enabled ? "ON" : "OFF")), false);
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.level != lastLevel) {
      lastLevel = mc.level;
      enabled = false;
      nextRequest = 0;
    }
    if (!enabled || mc.level == null || mc.player == null) return;
    if (mc.player.tickCount < nextRequest - 5) nextRequest = 0;
    if (mc.player.tickCount >= nextRequest) {
      nextRequest = mc.player.tickCount + 5;
      PacketDistributor.sendToServer(new BioshipNetwork.PathRequest());
    }
  }

  private static CompoundTag snapshot() {
    var mc = Minecraft.getInstance();
    if (!enabled || mc.player == null || mc.level == null || mc.options.hideGui)
      return new CompoundTag();
    var data = mc.player.getPersistentData();
    return mc.player.tickCount - data.getInt("PathReceived") > 20
        ? new CompoundTag()
        : data.getCompound(NavigationDebug.DATA);
  }

  private static String position(CompoundTag data, String key) {
    if (!data.contains(key, Tag.TAG_COMPOUND)) return "none";
    var p = VisionDebug.vector(data.getCompound(key));
    return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.x, p.y, p.z);
  }

  private static final class Lines extends RenderType {
    private Lines() {
      super(
          "unused",
          DefaultVertexFormat.POSITION_COLOR_NORMAL,
          VertexFormat.Mode.LINES,
          1536,
          false,
          false,
          () -> {},
          () -> {});
    }

    static final RenderType TYPE =
        create(
            "bioship_path_debug",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            true,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.of(2)))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .createCompositeState(false));
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
    var data = snapshot();
    if (!data.hasUUID("Ship")) return;
    var mc = Minecraft.getInstance();
    var pose = event.getPoseStack();
    pose.pushPose();
    var camera = event.getCamera().getPosition();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var buffer = buffers.getBuffer(Lines.TYPE);
    var origin = VisionDebug.vector(data.getCompound("Position"));
    marker(pose.last(), buffer, origin, .35, 0xffffffff);
    var route = data.getList("Route", Tag.TAG_COMPOUND);
    var previous = origin;
    for (int i = 0; i < route.size(); i++) {
      var point = VisionDebug.vector(route.getCompound(i).getCompound("Point"));
      line(
          pose.last(),
          buffer,
          previous,
          point,
          data.getBoolean("Walking") ? 0xffffd166 : 0xff777777);
      marker(pose.last(), buffer, point, i == 0 ? .25 : .1, i == 0 ? 0xff55ff77 : 0xffffd166);
      previous = point;
    }
    for (var key : List.of("Target", "Goal"))
      if (data.contains(key, Tag.TAG_COMPOUND)) {
        var point = VisionDebug.vector(data.getCompound(key));
        int color = key.equals("Goal") ? 0xff55dfff : 0xffff66dd;
        marker(pose.last(), buffer, point, .4, color);
        int segments = Math.min(128, Math.max(1, (int) Math.ceil(origin.distanceTo(point) * 2)));
        for (int i = 0; i < segments; i += 2)
          line(
              pose.last(),
              buffer,
              origin.lerp(point, (double) i / segments),
              origin.lerp(point, (double) Math.min(i + 1, segments) / segments),
              color);
      }
    hologram(data, pose, buffers);
    buffers.endBatch();
    pose.popPose();
  }

  private static String priority(CompoundTag entry, int index) {
    return String.format(
        Locale.ROOT,
        "%d. %s · %s · %.0f",
        index + 1,
        entry.getString("Band"),
        entry.getString("Action"),
        entry.getDouble("Weight"));
  }

  private static void hologram(
      CompoundTag data, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers) {
    var mc = Minecraft.getInstance();
    var words = new ArrayList<String>();
    words.add("BIOSHIP " + data.getUUID("Ship").toString().substring(0, 8));
    words.add(data.getString("Action") + " · " + data.getString("Status"));
    words.add("Movement goal: " + position(data, "Goal"));
    words.add(data.getString("Reason"));
    words.add("Target: " + data.getString("TargetLabel") + " · " + position(data, "Target"));
    if (data.getLong("TargetSeenAge") > 20)
      words.add(
          "Last seen "
              + data.getLong("TargetSeenAge") / 20
              + "s ago · memory expires in "
              + data.getLong("TargetMemoryMillis") / 1000.0
              + "s");
    words.add(
        String.format(
            Locale.ROOT,
            "Speed %.2f · %d waypoints",
            data.getDouble("Speed"),
            data.getInt("Waypoints")));
    words.add(
        data.getInt("CollectingArms")
            + " collecting arms · "
            + data.getInt("StoredComponents")
            + " stored parts · "
            + data.getInt("QueuedGoals")
            + " queued goals");
    words.add(
        "Needed: "
            + data.getInt("NeedFlesh")
            + " flesh / "
            + data.getInt("NeedFiber")
            + " fibers / "
            + data.getInt("NeedBone")
            + " bone");
    words.add("Digestive pairs needed: " + data.getInt("DigestiveDemand"));
    if (data.contains("EngineCapacity"))
      words.add("Prototype engine bays: " + data.getInt("EngineCapacity"));
    var body = data.getCompound("BodyWork");
    words.add("Body: " + body.getString("Action") + " · " + body.getString("Reason"));
    words.add("Body queue: " + body.getInt("QueuedGoals"));
    words.add("Priorities (needs before goals):");
    var priorities = data.getList("Priorities", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(5, priorities.size()); i++)
      words.add(priority(priorities.getCompound(i), i));
    var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
    for (var word : words) lines.addAll(mc.font.split(Component.literal(word), 260));
    int count = Math.min(20, lines.size());
    var anchor = VisionDebug.vector(data.getCompound("Hologram"));
    pose.pushPose();
    pose.translate(anchor.x, anchor.y + count * .25, anchor.z);
    pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
    pose.scale(.025f, -.025f, .025f);
    for (int i = 0; i < count; i++) {
      var line = lines.get(i);
      mc.font.drawInBatch(
          line,
          -mc.font.width(line) / 2f,
          i * 10,
          i == 0 ? 0xff70ecff : 0xffe2f4ff,
          false,
          pose.last().pose(),
          buffers,
          net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
          0x40000000,
          15728880);
    }
    pose.popPose();
  }

  private static void marker(
      PoseStack.Pose pose, VertexConsumer out, Vec3 p, double size, int color) {
    line(pose, out, p.add(-size, 0, 0), p.add(size, 0, 0), color);
    line(pose, out, p.add(0, -size, 0), p.add(0, size, 0), color);
    line(pose, out, p.add(0, 0, -size), p.add(0, 0, size), color);
  }

  private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, int color) {
    if (a.distanceToSqr(b) < 1e-9) return;
    var normal = b.subtract(a).normalize();
    out.addVertex(pose, (float) a.x, (float) a.y, (float) a.z)
        .setColor(color)
        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    out.addVertex(pose, (float) b.x, (float) b.y, (float) b.z)
        .setColor(color)
        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
  }
}
