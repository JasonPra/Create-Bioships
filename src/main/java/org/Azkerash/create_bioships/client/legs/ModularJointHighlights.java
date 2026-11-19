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

/** Tool feedback follows each component's actual shape and native sublevel render transform. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class ModularJointHighlights implements BlockEntityRenderer<ModularJointEntity> {
  public ModularJointHighlights(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(
      ModularJointEntity joint,
      float partial,
      PoseStack stack,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var player = Minecraft.getInstance().player;
    if (player == null || joint.getLevel() == null || !LimbPositionerActions.holding(player))
      return;
    var shape = joint.getBlockState().getShape(joint.getLevel(), joint.getBlockPos());
    if (shape.isEmpty()) return;
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
        ComponentRegistry.MODULAR_JOINT_ENTITY.get(), ModularJointHighlights::new);
  }
}
