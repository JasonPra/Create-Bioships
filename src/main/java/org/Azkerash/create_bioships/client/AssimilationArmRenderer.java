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
    BioshipHurtFlash.render(core, pose, buffers, light);
    if (core.coreMotion().active()) {
      double t =
          Math.clamp(
              (core.getLevel().getGameTime() - core.coreMotion().started() + (double) partial)
                  / core.coreMotion().duration(),
              0,
              1);
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
    if (core.rescue().active()) {
      double t = core.rescue().withdrawal();
      if (t < 1)
        for (var entry : core.rescue().tissue().entrySet()) {
          pose.pushPose();
          var offset = entry.getKey().subtract(core.getBlockPos());
          pose.translate(offset.getX() + t, offset.getY(), offset.getZ());
          pose.scale((float) (1 - t * .8), 1, 1);
          net.minecraft.client.Minecraft.getInstance()
              .getBlockRenderer()
              .renderSingleBlock(
                  entry.getValue(),
                  pose,
                  buffers,
                  light,
                  overlay,
                  net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                  RenderType.solid());
          pose.popPose();
        }
    }
    if (!core.body().dead()
        && (core.crawl().animating() || core.crawl().bracing() || core.aquatic().swimming()))
      renderLegs(core, partial, pose, buffers, light);
    boolean gathering =
        (core.rescue().carriedPosition() != null || core.crawl().reach() != null)
            && !core.body().dead();
    renderBatch(core, partial, pose, buffers, light);
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
      target =
          core.rescue().carriedPosition() != null
              ? core.rescue().carriedPosition()
              : core.crawl().reach();
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
    var slots = BioshipGraspers.slots(core);
    var targetLocal = reach;
    var nearest =
        slots.stream()
            .min(
                java.util.Comparator.comparingDouble(
                    s ->
                        s.root()
                            .subtract(Vec3.atLowerCornerOf(core.getBlockPos()))
                            .distanceToSqr(targetLocal)))
            .orElse(null);
    Vec3 root =
        nearest == null
            ? new Vec3(.5, .5, .5)
            : nearest.root().subtract(Vec3.atLowerCornerOf(core.getBlockPos()));
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
      segment.render(
          pose, consumer, light, BioshipHurtFlash.overlay(core, OverlayTexture.NO_OVERLAY));
      TentacleSuckers.render(
          pose, buffers, light, BioshipHurtFlash.overlay(core, OverlayTexture.NO_OVERLAY), i);
      pose.popPose();
      previous = point;
    }
  }

  private void renderBatch(
      LivingCoreBlockEntity core,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    if (core.getLevel() == null || core.body().dead()) return;
    var container = SubLevelContainer.getContainer(core.getLevel());
    if (container == null) return;
    var plot = container.getPlot(new ChunkPos(core.getBlockPos()));
    if (plot == null) return;
    var transform =
        plot.getSubLevel() instanceof ClientSubLevel client
            ? client.renderPose(partial)
            : plot.getSubLevel().logicalPose();
    for (var move : core.assimilation().arms()) {
      var cargo = container.getSubLevel(move.cargo());
      if (cargo == null) continue;
      var root = BioshipGraspers.root(core, core.assimilation().grasper(move.cargo()));
      var target =
          transform
              .transformPositionInverse(
                  cargo.logicalPose().transformPosition(Vec3.atCenterOf(move.cargoBlock())))
              .subtract(Vec3.atLowerCornerOf(core.getBlockPos()));
      var previous = root;
      var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
      for (int i = 1; i <= 14; i++) {
        double t = i / 14.0;
        var point = root.lerp(target, t).add(0, -Math.sin(Math.PI * t) * .35, 0);
        var delta = point.subtract(previous);
        if (delta.lengthSqr() < .00001) continue;
        pose.pushPose();
        pose.translate(previous.x, previous.y, previous.z);
        pose.mulPose(
            new Quaternionf()
                .rotationTo(
                    new Vector3f(0, -1, 0),
                    new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize()));
        float width = (float) (.5 - t * .25);
        pose.scale(width, (float) delta.length(), width);
        segment.render(
            pose, consumer, light, BioshipHurtFlash.overlay(core, OverlayTexture.NO_OVERLAY));
        TentacleSuckers.render(
            pose, buffers, light, BioshipHurtFlash.overlay(core, OverlayTexture.NO_OVERLAY), i);
        pose.popPose();
        previous = point;
      }
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
    var support = BioshipSupport.layout(core);
    int legs = support.legs().size();
    for (int leg = 0; leg < legs; leg++) {
      double side = leg % 2 == 0 ? -1 : 1;
      double front = leg < 2 ? -1 : 1;
      boolean moving = core.crawl().animating() || core.aquatic().swimming();
      double phase =
          core.crawl().gaitPhase()
              + Math.clamp(core.getLevel().getGameTime() + partial - core.crawl().gaitAt(), 0, 10)
                  * core.crawl().gaitSpeed()
                  / 20
                  * (scuttling ? 7 : 2)
              + ((leg / 2 + leg % 2) % 2 == 0 ? 0 : Math.PI);
      if (core.aquatic().swimming())
        phase = (core.getLevel().getGameTime() + partial) * .45 + leg * Math.PI / 2;
      Vec3 root = support.legs().get(leg).root();
      Vec3 base = support.legs().get(leg).foot();
      Vec3 foot =
          base.add(
              0,
              moving ? Math.max(0, Math.sin(phase)) * (scuttling ? .16 : .45) : 0,
              moving ? Math.cos(phase) * (scuttling ? .25 : .45) : 0);
      if (scuttling && core.crawl().wallFacing() != null) {
        var normal = Vec3.atLowerCornerOf(core.crawl().wallFacing().getNormal());
        var tangent =
            Math.abs(normal.y) > .5 ? new Vec3(1, 0, 0) : new Vec3(-normal.z, 0, normal.x);
        var second = normal.cross(tangent);
        foot =
            new Vec3(.5, .4, .5)
                .add(
                    normal.scale(
                        core.crawl().wallDistance()
                            - (moving ? Math.max(0, Math.sin(phase)) * .12 : 0)))
                .add(tangent.scale(side * .7))
                .add(second.scale(front * .65 + (moving ? Math.cos(phase) * .2 : 0)));
      }
      if (core.crawl().wallFacing() == null && !core.aquatic().swimming()) {
        var container = SubLevelContainer.getContainer(core.getLevel());
        var plot = container == null ? null : container.getPlot(new ChunkPos(core.getBlockPos()));
        if (plot != null) {
          var transform =
              plot.getSubLevel() instanceof ClientSubLevel client
                  ? client.renderPose(partial)
                  : plot.getSubLevel().logicalPose();
          var world =
              transform.transformPosition(
                  Vec3.atLowerCornerOf(core.structuralOrigin())
                      .add(foot.x, base.y + (scuttling ? .85 : 1.25), foot.z));
          for (int reach = 0; reach <= core.crawl().footingReach(); reach++) {
            var floor = net.minecraft.core.BlockPos.containing(world).below(reach);
            if (core.getLevel()
                .getBlockState(floor)
                .isFaceSturdy(core.getLevel(), floor, net.minecraft.core.Direction.UP)) {
              var local =
                  transform.transformPositionInverse(new Vec3(world.x, floor.getY() + 1, world.z));
              foot =
                  new Vec3(
                      foot.x,
                      local.y
                          - core.structuralOrigin().getY()
                          + (moving ? Math.max(0, Math.sin(phase)) * .16 : 0),
                      foot.z);
              break;
            }
          }
        }
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
        float width =
            (float) ((.5 - t * .25) * core.crawl().legThickness() * (scuttling ? 1 : 1.7));
        pose.scale(width, (float) delta.length(), width);
        segment.render(
            pose, consumer, light, BioshipHurtFlash.overlay(core, OverlayTexture.NO_OVERLAY));
        TentacleSuckers.render(
            pose, buffers, light, BioshipHurtFlash.overlay(core, OverlayTexture.NO_OVERLAY), i);
        pose.popPose();
        previous = point;
      }
    }
    pose.popPose();
  }
}
