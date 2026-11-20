package org.Azkerash.create_bioships.client.legs;

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
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.flipper.FlipperBlockEntity;
import org.joml.*;

/**
 * Opaque, block-entity-stage meshes use the native controller's IK samples on either kind of world.
 */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class FlipperRenderer implements BlockEntityRenderer<FlipperBlockEntity> {
  private static final List<ModelResourceLocation> PARTS =
      java.util.stream.IntStream.range(0, 3)
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
    for (int i = 0; i < 3; i++) {
      var p = points.get(i);
      var delta = points.get(i + 1).subtract(p);
      if (delta.lengthSqr() < 1e-8) {
        continue;
      }
      stack.pushPose();
      stack.translate(p.x, p.y, p.z);
      stack.mulPose(
          new Quaternionf()
              .rotationTo(
                  new Vector3f(0, 0, 1),
                  new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize()));
      stack.scale(1, 1, (float) (delta.length() / .28));
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
