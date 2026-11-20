package org.Azkerash.create_bioships.client.legs;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.Azkerash.create_bioships.compat.letitwalk.LimbPositionerActions;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.legs.ModularJointEntity;

/** Organic split joint meshes follow the native renderer's parent/knuckle/child transforms. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class ModularJointRenderer implements BlockEntityRenderer<ModularJointEntity> {
  public ModularJointRenderer(BlockEntityRendererProvider.Context context) {}

  private static final java.util.Map<
          String, net.minecraft.client.resources.model.ModelResourceLocation>
      PARTS = new java.util.HashMap<>();

  static {
    for (var kind : java.util.List.of("joint", "socket", "swivel")) {
      for (var part : java.util.List.of("base", "knuckle", "tip")) {
        String key = "limb_" + kind + "_" + part;
        PARTS.put(
            key,
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                net.minecraft.resources.ResourceLocation.parse("create_bioships:block/" + key)));
      }
    }
  }

  @SubscribeEvent
  public static void models(
      net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
    PARTS.values().forEach(event::register);
  }

  /** Use the upstream parent/knuckle/child poses; only the biological meshes differ. */
  public static java.util.List<dev.letitwalk.client.LegJointRenderer.Part> parts(
      ModularJointEntity joint, float partial) {
    var state = joint.getBlockState();
    String kind =
        state.getBlock()
                instanceof org.Azkerash.create_bioships.components.logic.legs.SocketJointBlock
            ? "socket"
            : ((dev.letitwalk.content.LegJointBlock) state.getBlock()).isSwivel()
                ? "swivel"
                : "joint";
    return dev.letitwalk.client.LegJointRenderer.pose(joint, partial).stream()
        .map(
            p -> {
              String name = p.model().id().getPath();
              String section =
                  name.endsWith("base") ? "base" : name.endsWith("knuckle") ? "knuckle" : "tip";
              return new dev.letitwalk.client.LegJointRenderer.Part(
                  PARTS.get("limb_" + kind + "_" + section), p.rotation());
            })
        .toList();
  }

  @Override
  public void render(
      ModularJointEntity joint,
      float partial,
      PoseStack stack,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (joint.getLevel() == null) {
      return;
    }
    var mc = Minecraft.getInstance();
    for (var part : parts(joint, partial)) {
      stack.pushPose();
      stack.translate(.5, .5, .5);
      stack.mulPose(part.rotation());
      stack.translate(-.5, -.5, -.5);
      var type = Sheets.cutoutBlockSheet();
      mc.getBlockRenderer()
          .getModelRenderer()
          .renderModel(
              stack.last(),
              buffers.getBuffer(type),
              null,
              mc.getModelManager().getModel(part.model()),
              1,
              1,
              1,
              light,
              overlay,
              net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
              type);
      stack.popPose();
    }
    var player = mc.player;
    if (player == null || joint.getLevel() == null || !LimbPositionerActions.holding(player)) {
      return;
    }
    var shape = joint.getBlockState().getShape(joint.getLevel(), joint.getBlockPos());
    if (shape.isEmpty()) {
      return;
    }
    LevelRenderer.renderLineBox(
        stack,
        buffers.getBuffer(RenderType.lines()),
        shape.bounds().inflate(.008),
        .8f,
        .4f,
        1f,
        1f);
  }

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(
        ComponentRegistry.MODULAR_JOINT_ENTITY.get(), ModularJointRenderer::new);
  }
}
