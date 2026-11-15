package org.Azkerash.create_bioships.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class AssimilationArmRenderer implements BlockEntityRenderer<LivingCoreBlockEntity> {
  private final ModelPart segment;
  private static final ResourceLocation SKIN =
      ResourceLocation.fromNamespaceAndPath(
          Create_bioships.MODID, "textures/block/living_hull.png");

  public AssimilationArmRenderer(BlockEntityRendererProvider.Context context) {
    var mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "arm",
            CubeListBuilder.create().texOffs(0, 0).addBox(-2, -16, -2, 4, 16, 4),
            net.minecraft.client.model.geom.PartPose.ZERO);
    segment = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("arm");
  }

  @Override
  public boolean shouldRenderOffScreen(LivingCoreBlockEntity core) {
    return true;
  }

  @Override
  public void render(
      LivingCoreBlockEntity core,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var move = core.organMove();
    if (move == null || !move.external() || core.getLevel() == null) return;
    var container = SubLevelContainer.getContainer(core.getLevel());
    if (container == null) return;
    var plot = container.getPlot(new ChunkPos(core.getBlockPos()));
    var cargo = container.getSubLevel(move.cargo());
    if (plot == null || cargo == null) return;
    var transform =
        plot.getSubLevel() instanceof ClientSubLevel client
            ? client.renderPose(partial)
            : plot.getSubLevel().logicalPose();
    var target = cargo.logicalPose().transformPosition(Vec3.atCenterOf(move.cargoBlock()));
    Vec3 reach =
        transform
            .transformPositionInverse(target)
            .subtract(Vec3.atLowerCornerOf(core.getBlockPos()));
    Vec3 root = new Vec3(.5, .5, .5);
    if (core.isMature()) {
      Vec3 d = reach.subtract(root);
      root =
          root.add(
              Math.copySign(Math.min(4, Math.abs(d.x)), d.x),
              1,
              Math.copySign(Math.min(7, Math.abs(d.z)), d.z));
    }
    double extension =
        Math.clamp((core.getLevel().getGameTime() + partial - move.started()) / 6.0, 0, 1);
    reach = root.lerp(reach, extension);
    Vec3 previous = root;
    var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
    for (int i = 1; i <= 14; i++) {
      double t = i / 14.0;
      Vec3 point =
          root.lerp(reach, t).add(0, -Math.sin(Math.PI * t) * .35, Math.sin(Math.PI * t) * .12);
      var delta = point.subtract(previous);
      if (delta.lengthSqr() < .00001) continue;
      pose.pushPose();
      pose.translate(previous.x, previous.y, previous.z);
      pose.mulPose(
          new Quaternionf()
              .rotationTo(
                  new Vector3f(0, -1, 0),
                  new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize()));
      float width = (float) (.55 - t * .25);
      pose.scale(width, (float) delta.length(), width);
      segment.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
      pose.popPose();
      previous = point;
    }
  }
}
