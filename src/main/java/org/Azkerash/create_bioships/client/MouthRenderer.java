package org.Azkerash.create_bioships.client;

import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.FeedingMouthBlockEntity;
import org.Azkerash.create_bioships.FeedingMouthBlock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.Azkerash.create_bioships.*;

/** Separate baked jaws articulate without moving the mouth block or its inventory. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class MouthRenderer implements BlockEntityRenderer<FeedingMouthBlockEntity> {
  public MouthRenderer(BlockEntityRendererProvider.Context context) {}

  private static ModelResourceLocation model(String name) {
    return ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(Create_bioships.MOD_ID, "block/mouth_" + name));
  }

  @SubscribeEvent
  public static void models(ModelEvent.RegisterAdditional event) {
    for (var name : java.util.List.of("shell", "upper", "lower")) event.register(model(name));
  }

  @Override
  public void render(
      FeedingMouthBlockEntity mouth,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    overlay = BioshipHurtFlash.overlay(mouth, overlay);
    pose.pushPose();
    pose.translate(.5, .5, .5);
    switch (mouth.getBlockState().getValue(FeedingMouthBlock.FACING)) {
      case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
      case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
      case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
      case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
      case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
      default -> {}
    }
    pose.translate(-.5, -.5, -.5);
    part(mouth, "shell", pose, buffers, light, overlay);
    double shift = mouth.jawShift(partial);
    pose.pushPose();
    pose.translate(0, -shift, 0);
    part(mouth, "upper", pose, buffers, light, overlay);
    pose.popPose();
    pose.pushPose();
    pose.translate(0, shift, 0);
    part(mouth, "lower", pose, buffers, light, overlay);
    pose.popPose();
    pose.popPose();
  }

  private static void part(
      FeedingMouthBlockEntity mouth,
      String name,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var mc = Minecraft.getInstance();
    mc.getBlockRenderer()
        .getModelRenderer()
        .renderModel(
            pose.last(),
            buffers.getBuffer(Sheets.cutoutBlockSheet()),
            mouth.getBlockState(),
            mc.getModelManager().getModel(model(name)),
            1,
            1,
            1,
            light,
            overlay,
            ModelData.EMPTY,
            RenderType.cutout());
  }
}
