package org.Azkerash.create_bioships.client.components;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.brain.BrainAppearancePayload;

/** One cached continuous organ mesh and one programmable flesh/bone access console per brain. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class BrainSurfaceRenderer {
  private record Snapshot(BrainAppearancePayload data, BrainSurface mesh, long at) {}

  private static final Map<UUID, Snapshot> BRAINS = new HashMap<>();
  private static net.minecraft.client.multiplayer.ClientLevel world;
  private static final ResourceLocation SKIN =
      ResourceLocation.parse("biomancy:textures/block/fibrous_flesh.png");
  private static final ModelResourceLocation PORT =
      ModelResourceLocation.standalone(ResourceLocation.parse("create_bioships:block/brain_port"));

  private BrainSurfaceRenderer() {}

  @SubscribeEvent
  public static void models(ModelEvent.RegisterAdditional event) {
    event.register(PORT);
  }

  public static void receive(BrainAppearancePayload data) {
    var level = Minecraft.getInstance().level;
    if (level != world) {
      BRAINS.clear();
      world = level;
    }
    if (level == null) return;
    if (data.cells().isEmpty()) {
      BRAINS.remove(data.body());
      return;
    }
    var previous = BRAINS.get(data.body());
    var cells = Set.copyOf(data.cells());
    var mesh =
        previous != null && Set.copyOf(previous.data.cells()).equals(cells)
            ? previous.mesh
            : new BrainSurface(cells);
    BRAINS.put(data.body(), new Snapshot(data, mesh, level.getGameTime()));
  }

  public static Vec3 surfaceOffset(UUID body, BlockPos cell, Direction face) {
    var snapshot = BRAINS.get(body);
    if (snapshot == null || !snapshot.data.cells().contains(cell)) return Vec3.ZERO;
    var flat =
        Vec3.atCenterOf(cell.subtract(snapshot.mesh.minimum))
            .add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
    return snapshot.mesh.surface(flat).subtract(flat);
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (world != mc.level) {
      BRAINS.clear();
      world = mc.level;
    }
    if (mc.level == null) return;
    var container = SubLevelContainer.getContainer(mc.level);
    if (container == null) return;
    BRAINS.entrySet().removeIf(e -> mc.level.getGameTime() - e.getValue().at > 100);
    var stack = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    var buffers = mc.renderBuffers().bufferSource();
    var type = RenderType.entityCutoutNoCull(SKIN);
    for (var snapshot : BRAINS.values()) {
      var body = container.getSubLevel(snapshot.data.body());
      if (!(body instanceof ClientSubLevel client) || body.isRemoved()) continue;
      var mesh = snapshot.mesh;
      var transform = client.renderPose();
      stack.pushPose();
      stack.translate(
          transform.position().x() - camera.x,
          transform.position().y() - camera.y,
          transform.position().z() - camera.z);
      stack.mulPose(new org.joml.Quaternionf(transform.orientation()));
      stack.scale(
          (float) transform.scale().x(),
          (float) transform.scale().y(),
          (float) transform.scale().z());
      stack.translate(
          mesh.minimum.getX() - transform.rotationPoint().x(),
          mesh.minimum.getY() - transform.rotationPoint().y(),
          mesh.minimum.getZ() - transform.rotationPoint().z());
      int light =
          LevelRenderer.getLightColor(
              mc.level,
              BlockPos.containing(
                  client.renderPose().transformPosition(Vec3.atCenterOf(mesh.minimum))));
      var vertices = buffers.getBuffer(type);
      for (var quad : mesh.quads) {
        vertex(vertices, stack, quad.a(), light);
        vertex(vertices, stack, quad.b(), light);
        vertex(vertices, stack, quad.c(), light);
        vertex(vertices, stack, quad.d(), light);
      }
      buffers.endBatch(type);
      var hurt = org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.hurtType(SKIN);
      if (hurt != null
          && org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.flashing(
              snapshot.data.body())) {
        var red = buffers.getBuffer(hurt);
        for (var q : mesh.quads) {
          hurtVertex(red, stack, q.a(), light);
          hurtVertex(red, stack, q.b(), light);
          hurtVertex(red, stack, q.c(), light);
          hurtVertex(red, stack, q.d(), light);
        }
        buffers.endBatch(hurt);
      }
      panel(mc, stack, buffers, snapshot, light);
      stack.popPose();
    }
    buffers.endBatch();
  }

  private static void vertex(
      VertexConsumer out, PoseStack stack, BrainSurface.Vertex v, int light) {
    var p = v.point();
    var n = v.normal();
    out.addVertex(stack.last().pose(), (float) p.x, (float) p.y, (float) p.z)
        .setColor(v.shade(), v.shade(), v.shade(), 1)
        .setUv(v.u(), v.v())
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(stack.last(), (float) n.x, (float) n.y, (float) n.z);
  }

  private static void hurtVertex(
      VertexConsumer out, PoseStack stack, BrainSurface.Vertex v, int light) {
    var p = v.point();
    var n = v.normal();
    out.addVertex(stack.last().pose(), (float) p.x, (float) p.y, (float) p.z)
        .setColor(1f, 1f, 1f, 1f)
        .setUv(v.u(), v.v())
        .setLight(light)
        .setNormal(stack.last(), (float) n.x, (float) n.y, (float) n.z);
  }

  private static void panel(
      Minecraft mc,
      PoseStack stack,
      MultiBufferSource.BufferSource buffers,
      Snapshot snapshot,
      int light) {
    var data = snapshot.data;
    var mesh = snapshot.mesh;
    var local = Vec3.atLowerCornerOf(data.port().subtract(mesh.minimum));
    var normal = Vec3.atLowerCornerOf(data.face().getNormal());
    var flat = local.add(.5, .5, .5).add(normal.scale(.5));
    var projected = mesh.surface(flat);
    var delta = projected.subtract(flat);
    stack.pushPose();
    stack.translate(local.x + delta.x, local.y + delta.y, local.z + delta.z);
    stack.translate(.5, .5, .5);
    switch (data.face()) {
      case SOUTH -> stack.mulPose(Axis.YP.rotationDegrees(180));
      case EAST -> stack.mulPose(Axis.YP.rotationDegrees(-90));
      case WEST -> stack.mulPose(Axis.YP.rotationDegrees(-270));
      case UP -> stack.mulPose(Axis.XP.rotationDegrees(-270));
      case DOWN -> stack.mulPose(Axis.XP.rotationDegrees(-90));
      default -> {}
    }
    stack.translate(-.5, -.5, -.5);
    var model = mc.getModelManager().getModel(PORT);
    mc.getBlockRenderer()
        .getModelRenderer()
        .renderModel(
            stack.last(),
            buffers.getBuffer(Sheets.cutoutBlockSheet()),
            ComponentRegistry.BRAIN.get().defaultBlockState(),
            model,
            1,
            1,
            1,
            light,
            OverlayTexture.NO_OVERLAY);
    buffers.endBatch(Sheets.cutoutBlockSheet());
    var hurt =
        org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.hurtType(
            net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
    if (hurt != null
        && org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.flashing(data.body()))
      mc.getBlockRenderer()
          .getModelRenderer()
          .renderModel(
              stack.last(),
              buffers.getBuffer(hurt),
              ComponentRegistry.BRAIN.get().defaultBlockState(),
              model,
              1,
              1,
              1,
              light,
              OverlayTexture.NO_OVERLAY);
    if (hurt != null) buffers.endBatch(hurt);
    if (data.alive()) {
      stack.translate(.5, .52, -3.68 / 16d);
      stack.mulPose(Axis.YP.rotationDegrees(180));
      stack.scale(.025f, -.025f, .025f);
      String text = mc.level.getGameTime() % 20 < 10 ? ">_" : ">";
      mc.font.drawInBatch(
          text,
          -mc.font.width(text) / 2f,
          -4,
          0xffb9f3cc,
          false,
          stack.last().pose(),
          buffers,
          net.minecraft.client.gui.Font.DisplayMode.NORMAL,
          0,
          0xf000f0);
      mc.font.drawInBatch(
          "{ }",
          -mc.font.width("{ }") / 2f,
          5,
          0xff758bce,
          false,
          stack.last().pose(),
          buffers,
          net.minecraft.client.gui.Font.DisplayMode.NORMAL,
          0,
          0xf000f0);
    }
    stack.popPose();
  }
}
