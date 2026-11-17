package org.Azkerash.create_bioships.client.combat;

import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.components.eye.*;

/** Material wounds fade/shrink independently; an injured eye stays visibly shut until healed. */
@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT)
public final class SubLevelWoundRenderer {
  private record Key(UUID body, BlockPos cell, Direction face) {}

  private static final Map<Key, WoundPayload> WOUNDS = new LinkedHashMap<>();
  private static net.minecraft.client.multiplayer.ClientLevel world;
  private static final ResourceLocation
      BLOOD =
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "textures/effect/wound.png"),
      SKIN =
          ResourceLocation.fromNamespaceAndPath(
              Create_bioships.MODID, "textures/block/living_core.png");

  private SubLevelWoundRenderer() {}

  public static void receive(WoundPayload wound) {
    var level = Minecraft.getInstance().level;
    if (level == null) return;
    if (world != level) {
      WOUNDS.clear();
      world = level;
    }
    WOUNDS.put(new Key(wound.body(), wound.cell(), wound.face()), wound);
    while (WOUNDS.size() > 512) WOUNDS.remove(WOUNDS.keySet().iterator().next());
  }

  public static boolean injured(UUID body, BlockPos cell, Direction face, long time) {
    var wound = WOUNDS.get(new Key(body, cell, face));
    return wound != null && time < wound.at() + wound.duration();
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.level != world) {
      WOUNDS.clear();
      world = mc.level;
    }
    if (mc.level == null) return;
    var container = SubLevelContainer.getContainer(mc.level);
    if (container == null) return;
    var buffers = mc.renderBuffers().bufferSource();
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    WOUNDS.values().removeIf(w -> mc.level.getGameTime() >= w.at() + w.duration());
    for (var wound : WOUNDS.values()) {
      var body = container.getSubLevel(wound.body());
      if (!(body instanceof ClientSubLevel client) || body.isRemoved()) continue;
      var state = mc.level.getBlockState(wound.cell());
      if (state.isAir()) continue;
      var shape = state.getShape(mc.level, wound.cell());
      if (shape.isEmpty()) continue;
      var box = shape.bounds();
      var t = client.renderPose();
      pose.pushPose();
      pose.translate(
          t.position().x() - camera.x, t.position().y() - camera.y, t.position().z() - camera.z);
      pose.mulPose(new org.joml.Quaternionf(t.orientation()));
      pose.scale((float) t.scale().x(), (float) t.scale().y(), (float) t.scale().z());
      pose.translate(
          wound.cell().getX() - t.rotationPoint().x(),
          wound.cell().getY() - t.rotationPoint().y(),
          wound.cell().getZ() - t.rotationPoint().z());
      int light = LevelRenderer.getLightColor(mc.level, wound.cell());
      if (state.is(Create_bioships.CORE.get())) {
        var skin = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
        quad(
            pose,
            skin,
            wound.face(),
            box,
            .125f,
            .25f,
            .875f,
            .75f,
            0,
            0,
            .25f,
            .25f,
            1,
            light,
            .004f);
        quad(
            pose,
            skin,
            wound.face(),
            box,
            .125f,
            .484f,
            .875f,
            .516f,
            .4375f,
            .4375f,
            .5f,
            .5f,
            1,
            light,
            .006f);
      } else if (EyeWoundBehavior.isWeakPoint(state, wound.face())) {
        var closed = state.setValue(EyeBlink.BLINK, true);
        var model = mc.getBlockRenderer().getBlockModel(closed);
        var consumer =
            buffers.getBuffer(
                RenderType.entityCutoutNoCull(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.scale(1.003f, 1.003f, 1.003f);
        pose.translate(-.5, -.5, -.5);
        for (var q : model.getQuads(closed, null, net.minecraft.util.RandomSource.create()))
          consumer.putBulkData(pose.last(), q, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
      }
      float
          age = Math.clamp((mc.level.getGameTime() - wound.at()) / (float) wound.duration(), 0, 1),
          size = .35f * (1 - .65f * age);
      quad(
          pose,
          buffers.getBuffer(RenderType.entityTranslucent(BLOOD)),
          wound.face(),
          box,
          .5f - size,
          .5f - size,
          .5f + size,
          .5f + size,
          0,
          0,
          1,
          1,
          1 - age,
          light,
          .009f);
      pose.popPose();
    }
    if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
      var lines = buffers.getBuffer(RenderType.lines());
      pose.pushPose();
      pose.translate(-camera.x, -camera.y, -camera.z);
      for (var e : mc.level.entitiesForRendering())
        if (e instanceof EyeCombatHurtbox eye && eye.gridId().isPresent()) {
          var body = container.getSubLevel(eye.gridId().get());
          if (body instanceof ClientSubLevel client) {
            var shape = mc.level.getBlockState(eye.cell()).getShape(mc.level, eye.cell());
            if (!shape.isEmpty()) {
              var box =
                  new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                      shape.bounds().move(eye.cell()));
              LevelRenderer.renderLineBox(
                  pose,
                  lines,
                  box.transform(client.renderPose(), box).toMojang(),
                  1,
                  .25f,
                  .15f,
                  1);
            }
          }
        }
      pose.popPose();
      buffers.endBatch(RenderType.lines());
    }
    buffers.endBatch(RenderType.entityTranslucent(BLOOD));
    buffers.endBatch(RenderType.entityCutoutNoCull(SKIN));
    buffers.endBatch(
        RenderType.entityCutoutNoCull(
            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
  }

  private static void quad(
      PoseStack pose,
      VertexConsumer out,
      Direction face,
      AABB box,
      float u0,
      float v0,
      float u1,
      float v1,
      float tx0,
      float ty0,
      float tx1,
      float ty1,
      float alpha,
      int light,
      float offset) {
    double minU = face.getAxis() == Direction.Axis.X ? box.minZ : box.minX,
        maxU = face.getAxis() == Direction.Axis.X ? box.maxZ : box.maxX;
    double minV = face.getAxis() == Direction.Axis.Y ? box.minZ : box.minY,
        maxV = face.getAxis() == Direction.Axis.Y ? box.maxZ : box.maxY;
    double plane =
        switch (face) {
          case NORTH -> box.minZ - offset;
          case SOUTH -> box.maxZ + offset;
          case WEST -> box.minX - offset;
          case EAST -> box.maxX + offset;
          case DOWN -> box.minY - offset;
          case UP -> box.maxY + offset;
        };
    float[] us = {u0, u0, u1, u1},
        vs = {v0, v1, v1, v0},
        tx = {tx0, tx0, tx1, tx1},
        ty = {ty1, ty0, ty0, ty1};
    for (int i = 0; i < 4; i++) {
      float u = (float) (minU + (maxU - minU) * us[i]), v = (float) (minV + (maxV - minV) * vs[i]);
      float x = face.getAxis() == Direction.Axis.X ? (float) plane : u,
          y = face.getAxis() == Direction.Axis.Y ? (float) plane : v,
          z =
              face.getAxis() == Direction.Axis.Z
                  ? (float) plane
                  : face.getAxis() == Direction.Axis.X ? u : v;
      out.addVertex(pose.last(), x, y, z)
          .setColor(1, 1, 1, alpha)
          .setUv(tx[i], ty[i])
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(light)
          .setNormal(pose.last(), face.getStepX(), face.getStepY(), face.getStepZ());
    }
  }
}
