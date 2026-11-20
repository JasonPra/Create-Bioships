package org.Azkerash.create_bioships.client.legs;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.legs.*;

/** Segmented exoskeleton meshes follow the server's native Let It Walk IK solution. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class CrabLegRenderer {
  private record Snapshot(LegPosePayload previous, LegPosePayload current, long at) {}

  private static final Map<UUID, Snapshot> POSES = new HashMap<>();
  private static net.minecraft.client.multiplayer.ClientLevel world;

  private CrabLegRenderer() {}

  public static void receive(LegPosePayload payload) {
    var level = Minecraft.getInstance().level;
    if (world != level) {
      POSES.clear();
      world = level;
    }
    if (level == null) {
      return;
    }
    var old = POSES.get(payload.body());
    POSES.put(
        payload.body(),
        new Snapshot(old == null ? payload : old.current, payload, level.getGameTime()));
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
      return;
    }
    var mc = Minecraft.getInstance();
    if (world != mc.level) {
      POSES.clear();
      world = mc.level;
    }
    if (mc.level == null) {
      return;
    }
    var container = SubLevelContainer.getContainer(mc.level);
    if (container == null) {
      return;
    }
    POSES.entrySet().removeIf(e -> mc.level.getGameTime() - e.getValue().at > 40);
    var stack = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    for (var snapshot : POSES.values()) {
      var body = container.getSubLevel(snapshot.current.body());
      if (!(body instanceof ClientSubLevel client) || body.isRemoved()) {
        continue;
      }
      var t = client.renderPose();
      stack.pushPose();
      stack.translate(
          t.position().x() - camera.x, t.position().y() - camera.y, t.position().z() - camera.z);
      stack.mulPose(new org.joml.Quaternionf(t.orientation()));
      stack.scale((float) t.scale().x(), (float) t.scale().y(), (float) t.scale().z());
      var pivot = new Vec3(t.rotationPoint().x(), t.rotationPoint().y(), t.rotationPoint().z());
      double mix =
          Math.clamp(
              (mc.level.getGameTime()
                      - snapshot.at
                      + event.getPartialTick().getGameTimeDeltaPartialTick(false))
                  / 4d,
              0,
              1);
      var hurt =
          org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.flashing(
                  snapshot.current.body())
              ? org.Azkerash.create_bioships.client.combat.SubLevelHurtRenderer.hurtType(
                  net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
              : null;
      var previousPoses =
          new HashMap<
              net.minecraft.core.BlockPos,
              org.Azkerash.create_bioships.core.legs.SubLevelLegs.Pose>();
      for (var old : snapshot.previous.poses()) {
        previousPoses.put(old.cell(), old);
      }
      for (var pose : snapshot.current.poses()) {
        if (!mc.level.getBlockState(pose.cell()).is(ComponentRegistry.CRAB_LEG.get())) {
          continue;
        }
        var previous = previousPoses.getOrDefault(pose.cell(), pose);
        for (int i = 0; i < 3; i++) {
          var from =
              previous
                  .collisionEndpoints()
                  .get(i * 2)
                  .lerp(pose.collisionEndpoints().get(i * 2), mix);
          var to =
              previous
                  .collisionEndpoints()
                  .get(i * 2 + 1)
                  .lerp(pose.collisionEndpoints().get(i * 2 + 1), mix);
          segment(
              stack,
              mc,
              from.subtract(pivot),
              to.subtract(pivot),
              (float)
                  org.Azkerash.create_bioships.compat.letitwalk.CompactLegCollisions.width(
                      i,
                      mc.level
                          .getBlockState(pose.cell())
                          .getValue(
                              org.Azkerash.create_bioships
                                  .components
                                  .logic
                                  .legs
                                  .CrabLegBlock
                                  .HEIGHT)),
              hurt);
        }
        double size =
            .24
                * org.Azkerash.create_bioships.compat.letitwalk.CrabLegKinematics.thickness(
                    mc.level
                        .getBlockState(pose.cell())
                        .getValue(
                            org.Azkerash.create_bioships
                                .components
                                .logic
                                .legs
                                .CrabLegBlock
                                .HEIGHT));
        for (int joint = 0; joint < 3; joint++) {
          var point =
              previous
                  .collisionEndpoints()
                  .get(joint * 2)
                  .lerp(pose.collisionEndpoints().get(joint * 2), mix)
                  .subtract(pivot);
          socket(stack, mc, point, size, hurt);
        }
      }
      stack.popPose();
    }
    mc.renderBuffers().bufferSource().endBatch();
  }

  private static void socket(
      com.mojang.blaze3d.vertex.PoseStack stack,
      Minecraft mc,
      Vec3 point,
      double size,
      net.minecraft.client.renderer.RenderType hurt) {
    stack.pushPose();
    stack.translate(point.x, point.y - size / 2, point.z);
    int light =
        net.minecraft.client.renderer.LevelRenderer.getLightColor(
            mc.level, mc.gameRenderer.getMainCamera().getBlockPosition());
    CrabLegMesh.render(stack, mc, size, size, light, hurt);
    stack.translate(0, size * .15, 0);
    CrabLegMesh.render(stack, mc, size * 1.18, size * .7, light, hurt);
    stack.translate(0, -size * .15, 0);
    if (mc.player != null
        && org.Azkerash.create_bioships.compat.letitwalk.LimbPositionerActions.holding(mc.player)) {
      net.minecraft.client.renderer.LevelRenderer.renderLineBox(
          stack,
          mc.renderBuffers()
              .bufferSource()
              .getBuffer(net.minecraft.client.renderer.RenderType.lines()),
          -size * .7,
          -size * .2,
          -size * .7,
          size * .7,
          size * 1.2,
          size * .7,
          .8f,
          .4f,
          1f,
          1f);
    }
    stack.popPose();
  }

  private static void segment(
      com.mojang.blaze3d.vertex.PoseStack stack,
      Minecraft mc,
      Vec3 from,
      Vec3 to,
      float width,
      net.minecraft.client.renderer.RenderType hurt) {
    var delta = to.subtract(from);
    if (delta.lengthSqr() < .00001) {
      return;
    }
    stack.pushPose();
    stack.translate(from.x, from.y, from.z);
    var direction =
        new org.joml.Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize();
    stack.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), direction));
    var world = mc.gameRenderer.getMainCamera().getBlockPosition();
    int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, world);
    CrabLegMesh.render(stack, mc, width, delta.length(), light, hurt);
    if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
      net.minecraft.client.renderer.LevelRenderer.renderLineBox(
          stack,
          mc.renderBuffers()
              .bufferSource()
              .getBuffer(net.minecraft.client.renderer.RenderType.lines()),
          -width / 2,
          0,
          -width / 2,
          width / 2,
          delta.length(),
          width / 2,
          1,
          1,
          1,
          1);
    }
    stack.popPose();
  }
}
