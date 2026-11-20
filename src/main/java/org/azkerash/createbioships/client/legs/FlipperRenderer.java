package org.azkerash.createbioships.client.legs;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
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
import org.azkerash.createbioships.components.logic.flipper.FlipperBlockEntity;
import org.azkerash.createbioships.core.flippers.FlipperGeometry;

/** Opaque paddle sections follow fixed-length rowing samples on normal worlds and sublevels. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class FlipperRenderer implements BlockEntityRenderer<FlipperBlockEntity> {
  private static final List<ModelResourceLocation> PARTS =
      java.util.stream.IntStream.range(0, FlipperGeometry.SECTIONS)
          .mapToObj(
              i ->
                  ModelResourceLocation.standalone(
                      ResourceLocation.parse("create_bioships:block/flipper_paddle_" + i)))
          .toList();

  public FlipperRenderer(BlockEntityRendererProvider.Context context) {}

  @SubscribeEvent
  public static void models(ModelEvent.RegisterAdditional e) {
    PARTS.forEach(e::register);
  }

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers e) {
    e.registerBlockEntityRenderer(ComponentRegistry.FLIPPER_ENTITY.get(), FlipperRenderer::new);
  }

  @Override
  public net.minecraft.world.phys.AABB getRenderBoundingBox(FlipperBlockEntity flipper) {
    return new net.minecraft.world.phys.AABB(flipper.getBlockPos()).inflate(FlipperGeometry.LENGTH);
  }

  @Override
  public void render(
      FlipperBlockEntity fin,
      float partial,
      PoseStack stack,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var mc = Minecraft.getInstance();
    var points = fin.points(partial);
    var type = Sheets.cutoutBlockSheet();
    for (int i = 0; i < FlipperGeometry.SECTIONS; i++) {
      var p = points.get(i);
      var delta = points.get(i + 1).subtract(p);
      if (delta.lengthSqr() < 1e-8) {
        continue;
      }
      stack.pushPose();
      stack.translate(p.x, p.y, p.z);
      stack.mulPose(FlipperGeometry.orientation(delta));
      stack.scale(1, 1, (float) (delta.length() / FlipperGeometry.SECTION_LENGTH));
      mc.getBlockRenderer()
          .getModelRenderer()
          .renderModel(
              stack.last(),
              buffers.getBuffer(type),
              null,
              mc.getModelManager().getModel(PARTS.get(i)),
              1,
              1,
              1,
              light,
              overlay,
              net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
              type);
      stack.popPose();
    }
  }
}
