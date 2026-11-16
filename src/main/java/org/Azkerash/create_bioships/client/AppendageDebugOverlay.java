package org.Azkerash.create_bioships.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.Azkerash.create_bioships.*;

/** F3+B shows the world-axis collision volumes, independent of the rendered body's rotation. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class AppendageDebugOverlay {
  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    var mc = Minecraft.getInstance();
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
        || mc.level == null
        || !mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;
    var container = SubLevelContainer.getContainer(mc.level);
    if (container == null) return;
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
    for (var ship : container.getAllSubLevels())
      for (var chunk : ship.getPlot().getLoadedChunks())
        for (var entity : chunk.getChunk().getBlockEntities().values()) {
          if (!(entity instanceof LivingCoreBlockEntity core) || core.body().dead()) continue;
          var origin =
              ship.logicalPose()
                  .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(core.getBlockPos()));
          if (origin.distanceToSqr(camera) > 128 * 128) continue;
          for (int leg = 0; leg < 32; leg++) {
            var tip =
                core.crawl()
                    .legAnchors()
                    .foot(
                        leg,
                        mc.level.getGameTime()
                            + event.getPartialTick().getGameTimeDeltaPartialTick(false));
            if (tip != null)
              LevelRenderer.renderLineBox(
                  pose,
                  buffer,
                  new AABB(tip, tip).inflate(BioshipLegAnchors.TIP_RADIUS),
                  .2f,
                  1f,
                  .4f,
                  1f);
          }
          var tip = core.attackTip();
          if (core.attackSweeping() && tip != null)
            LevelRenderer.renderLineBox(
                pose,
                buffer,
                new AABB(tip, tip).inflate(TentacleStrike.TIP_HALF_SIZE),
                1f,
                .2f,
                .2f,
                1f);
        }
    pose.popPose();
  }
}
