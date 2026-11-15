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
          Create_bioships.MOD_ID, "textures/block/living_hull.png");

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
    if (core.coreMotion().active()) {
      double t =
          Math.clamp(
              (core.getLevel().getGameTime() + partial - core.coreMotion().started()) / 12.0, 0, 1);
      var delta = core.coreMotion().target().subtract(core.getBlockPos());
      pose.pushPose();
      pose.translate(delta.getX() * t, delta.getY() * t, delta.getZ() * t);
      net.minecraft.client.Minecraft.getInstance()
          .getBlockRenderer()
          .renderSingleBlock(
              core.getBlockState().setValue(LivingCoreBlock.RELOCATING, false),
              pose,
              buffers,
              light,
              overlay,
              net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
              RenderType.solid());
      pose.popPose();
    }
    if (!core.body().dead() && (core.crawl().walking() || core.crawl().bracing()))
      renderLegs(core, partial, pose, buffers, light);
    boolean gathering = core.crawl().reach() != null && !core.body().dead();
    var move = core.organMove();
    boolean carrying = core.blueprint().carried() != null && core.blueprint().carryStart() != null;
    if (core.getLevel() == null || !gathering && !carrying && (move == null || !move.usesArm()))
      return;
    var container = SubLevelContainer.getContainer(core.getLevel());
    if (container == null) return;
    var plot = container.getPlot(new ChunkPos(core.getBlockPos()));
    if (plot == null) return;
    var transform =
        plot.getSubLevel() instanceof ClientSubLevel client
            ? client.renderPose(partial)
            : plot.getSubLevel().logicalPose();
    Vec3 target;
    if (gathering) {
      target = core.crawl().reach();
    } else if (carrying) {
      double t = Math.clamp((core.blueprint().carryTicks() + partial) / 60.0, 0, 1);
      target =
          core.blueprint()
              .carryStart()
              .lerp(core.blueprint().carryEnd(), t)
              .add(0, Math.sin(Math.PI * t) * 3 + .8, 0);
    } else {
      var cargo = container.getSubLevel(move.cargo());
      if (cargo == null) return;
      target = cargo.logicalPose().transformPosition(Vec3.atCenterOf(move.cargoBlock()));
    }
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
        carrying || gathering
            ? 1
            : Math.clamp((core.getLevel().getGameTime() + partial - move.started()) / 6.0, 0, 1);
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

  private void renderLegs(
      LivingCoreBlockEntity core,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    pose.pushPose();
    var anchor = core.structuralOrigin().subtract(core.getBlockPos());
    pose.translate(anchor.getX(), anchor.getY(), anchor.getZ());
    var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
    boolean scuttling = core.hatchling();
    int legs = 4;
    for (int leg = 0; leg < legs; leg++) {
      double side = leg % 2 == 0 ? -1 : 1;
      double front = leg < 2 ? -1 : 1;
      double phase =
          (core.getLevel().getGameTime() + partial) * (scuttling ? 1.05 : .22)
              + ((leg / 2 + leg % 2) % 2 == 0 ? 0 : Math.PI);
      boolean moving = core.crawl().walking();
      Vec3 root = new Vec3(.5 + side * .4, .4, .5 + front * .4);
      Vec3 foot =
          new Vec3(
              .5 + side * (scuttling ? 1.65 : 1.5),
              (scuttling ? -.35 : -1.25)
                  + (moving ? Math.max(0, Math.sin(phase)) * (scuttling ? .16 : .45) : 0),
              .5 + front * 1.2 + (moving ? Math.cos(phase) * (scuttling ? .25 : .45) : 0));
      if (scuttling && core.crawl().wallFacing() != null) {
        var normal = Vec3.atLowerCornerOf(core.crawl().wallFacing().getNormal());
        var tangent = new Vec3(-normal.z, 0, normal.x);
        foot =
            new Vec3(.5, .4, .5)
                .add(
                    normal.scale(
                        core.crawl().wallDistance()
                            - (moving ? Math.max(0, Math.sin(phase)) * .12 : 0)))
                .add(tangent.scale(side * .7))
                .add(0, front * .65 + (moving ? Math.cos(phase) * .2 : 0), 0);
      }
      Vec3 previous = root;
      for (int i = 1; i <= 10; i++) {
        double t = i / 10.0;
        Vec3 point =
            root.lerp(foot, t)
                .add(
                    side * Math.sin(Math.PI * t) * .3,
                    Math.sin(Math.PI * t) * (scuttling ? .25 : .6),
                    0);
        Vec3 delta = point.subtract(previous);
        pose.pushPose();
        pose.translate(previous.x, previous.y, previous.z);
        pose.mulPose(
            new Quaternionf()
                .rotationTo(
                    new Vector3f(0, -1, 0),
                    new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize()));
        float width = (float) (.5 - t * .25);
        pose.scale(width, (float) delta.length(), width);
        segment.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        previous = point;
      }
    }
    pose.popPose();
  }
}
