package org.azkerash.createbioships.client.mouth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.mouth.*;

/**
 * Independently hinged chelicerae follow normal block/Sable transforms, including underside mounts.
 */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class ArachnidMouthRenderer implements BlockEntityRenderer<MouthBlockEntity> {
  private static final List<ModelResourceLocation> PARTS =
      List.of("left_jaw", "left_fang", "right_jaw", "right_fang").stream()
          .map(
              name ->
                  ModelResourceLocation.standalone(
                      ResourceLocation.parse("create_bioships:block/arachnid_" + name)))
          .toList();

  public ArachnidMouthRenderer(BlockEntityRendererProvider.Context context) {}

  @SubscribeEvent
  public static void models(ModelEvent.RegisterAdditional event) {
    PARTS.forEach(event::register);
  }

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(
        ComponentRegistry.MOUTH_ENTITY.get(), ArachnidMouthRenderer::new);
  }

  @Override
  public net.minecraft.world.phys.AABB getRenderBoundingBox(MouthBlockEntity mouth) {
    return new net.minecraft.world.phys.AABB(mouth.getBlockPos())
        .inflate(MouthBlock.isArachnid(mouth.getBlockState()) ? 1.5 : 0);
  }

  private static void part(
      int index, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
    var mc = Minecraft.getInstance();
    var type = Sheets.cutoutBlockSheet();
    mc.getBlockRenderer()
        .getModelRenderer()
        .renderModel(
            stack.last(),
            buffers.getBuffer(type),
            null,
            mc.getModelManager().getModel(PARTS.get(index)),
            1,
            1,
            1,
            light,
            overlay,
            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
            type);
  }

  @Override
  public void render(
      MouthBlockEntity mouth,
      float partial,
      PoseStack stack,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (!MouthBlock.isArachnid(mouth.getBlockState())) {
      return;
    }
    var face = mouth.getBlockState().getValue(MouthBlock.FACING);
    double opening = mouth.mandibleOpening(partial);
    stack.pushPose();
    stack.translate(.5, .5, .5);
    stack.mulPose(
        new org.joml.Quaternionf()
            .rotationTo(
                new org.joml.Vector3f(0, 0, -1),
                new org.joml.Vector3f(face.getStepX(), face.getStepY(), face.getStepZ())));
    stack.translate(-.5, -.5, -.5);
    for (int index = 0; index < 2; index++) {
      int side = index == 0 ? 1 : -1;
      stack.pushPose();
      stack.translate(
          (index == 0 ? 3d : 13d) / 16, MandibleGeometry.HINGE_Y, MandibleGeometry.HINGE_Z);
      stack.mulPose(Axis.YP.rotationDegrees((float) MandibleGeometry.jawYaw(side, opening)));
      part(index * 2, stack, buffers, light, overlay);
      stack.translate(side / 16d, -1d / 16, -10d / 16);
      stack.mulPose(Axis.XP.rotationDegrees((float) MandibleGeometry.fangPitch(opening)));
      part(index * 2 + 1, stack, buffers, light, overlay);
      stack.popPose();
    }
    stack.popPose();
  }
}
