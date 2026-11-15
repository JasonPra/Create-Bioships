package org.Azkerash.create_bioships.client;

import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Continuous travelling waves propel the body; captured targets bend the same limb toward prey. */
public class FlagellumRenderer implements BlockEntityRenderer<TentacleBlockEntity> {
  private static final ResourceLocation SKIN =
      ResourceLocation.fromNamespaceAndPath(
          Create_bioships.MOD_ID, "textures/block/living_hull.png");
  private final ModelPart segment;

  public FlagellumRenderer(BlockEntityRendererProvider.Context context) {
    MeshDefinition mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "segment",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3, -16, -3, 6, 16, 6),
            net.minecraft.client.model.geom.PartPose.ZERO);
    segment = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("segment");
  }

  @Override
  public boolean shouldRenderOffScreen(TentacleBlockEntity entity) {
    return true;
  }

  @Override
  public void render(
      TentacleBlockEntity limb,
      float partialTick,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (limb.getLevel() == null) return;
    var container = SubLevelContainer.getContainer(limb.getLevel());
    if (container == null || !container.inBounds(limb.getBlockPos())) return;
    var plot = container.getPlot(new ChunkPos(limb.getBlockPos()));
    if (plot == null) return;
    Entity target =
        limb.targetEntityId() < 0 ? null : limb.getLevel().getEntity(limb.targetEntityId());
    Vec3 reach = null;
    if (target != null) {
      var transform =
          plot.getSubLevel() instanceof ClientSubLevel client
              ? client.renderPose(partialTick)
              : plot.getSubLevel().logicalPose();
      var worldTarget =
          limb.strike().active()
              ? limb.strike()
                  .tip(
                      transform
                          .transformPosition(Vec3.atCenterOf(limb.getBlockPos()))
                          .add(0, -.7, 0),
                      limb.getLevel().getGameTime() + partialTick,
                      8)
              : target.getBoundingBox().getCenter();
      reach =
          transform
              .transformPositionInverse(worldTarget)
              .subtract(Vec3.atLowerCornerOf(limb.getBlockPos()).add(0.5, 1, 0.5));
    }
    double time = limb.getLevel().getGameTime() + partialTick;
    double directionSign = 1;
    for (BlockPos root : DinghyGrowth.FLAGELLA) {
      BlockPos core = limb.getBlockPos().subtract(root);
      if (limb.getLevel().getBlockState(core).is(Create_bioships.CORE.get())) {
        var throttle = limb.getLevel().getBlockState(core.offset(DinghyGrowth.THROTTLE));
        if (throttle.is(Create_bioships.THROTTLE.get()) && LivingThrottleBlock.signed(throttle) < 0)
          directionSign = -1;
        break;
      }
    }
    double phase = (limb.getBlockPos().getX() & 15) * 0.6 + (limb.getBlockPos().getZ() & 15) * 0.8;
    double speed =
        Math.min(
            1,
            plot.getSubLevel() instanceof ClientSubLevel
                ? plot.getSubLevel()
                        .logicalPose()
                        .position()
                        .distance(plot.getSubLevel().lastPose().position())
                    * 12
                : 0);
    var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
    Vec3 previous = Vec3.ZERO;
    pose.pushPose();
    pose.translate(0.5, 1, 0.5);
    for (int n = 1; n <= 8; n++) {
      double t = n / 8.0;
      Vec3 point;
      if (reach != null)
        point = reach.scale(t).add(Math.sin(Math.PI * t) * 0.3, -Math.sin(Math.PI * t) * 0.5, 0);
      else
        point =
            new Vec3(
                Math.sin(time * 0.21 * directionSign - t * 5 + phase) * (0.15 + speed * 0.45) * t,
                -t * 4.2,
                Math.cos(time * 0.21 * directionSign - t * 5 + phase) * (0.2 + speed * 0.7) * t);
      Vec3 direction = point.subtract(previous);
      pose.pushPose();
      pose.translate(previous.x, previous.y, previous.z);
      pose.mulPose(
          new Quaternionf()
              .rotationTo(
                  new Vector3f(0, -1, 0),
                  new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)
                      .normalize()));
      float thickness = (float) (1 - t * 0.72);
      pose.scale(thickness, (float) direction.length(), thickness);
      segment.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
      pose.popPose();
      previous = point;
    }
    pose.popPose();
  }
}
