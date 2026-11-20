package org.Azkerash.create_bioships.client.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.nametag.NameTagDisplayEntity;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class NameTagDisplayRenderer implements BlockEntityRenderer<NameTagDisplayEntity> {
  public NameTagDisplayRenderer(BlockEntityRendererProvider.Context context) {}

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers e) {
    e.registerBlockEntityRenderer(
        ComponentRegistry.NAME_TAG_DISPLAY_ENTITY.get(), NameTagDisplayRenderer::new);
  }

  @Override
  public boolean shouldRenderOffScreen(NameTagDisplayEntity panel) {
    return true;
  }

  @Override
  public void render(
      NameTagDisplayEntity panel,
      float partial,
      PoseStack stack,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (panel.getLevel() == null || panel.label().getString().isBlank()) {
      return;
    }
    var mc = Minecraft.getInstance();
    var o = panel.offset();
    stack.pushPose();
    stack.translate(.5 + o.x / 16d, .5 + o.y / 16d, .5 + o.z / 16d);
    var container = SubLevelContainer.getContainer(panel.getLevel());
    if (container != null && container.inBounds(panel.getBlockPos())) {
      var plot = container.getPlot(new ChunkPos(panel.getBlockPos()));
      if (plot != null && plot.getSubLevel() instanceof ClientSubLevel body) {
        stack.mulPose(new Quaternionf(body.renderPose().orientation()).conjugate());
      }
    }
    // Mounting FACING is deliberately ignored. This is a world-upright billboard, even below a
    // hull.
    stack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
    stack.scale(.025f, -.025f, .025f);
    // Flush text in the opaque block-entity pass, before glass writes its depth.
    var textBuffers = mc.renderBuffers().bufferSource();
    mc.font.drawInBatch(
        panel.label(),
        -mc.font.width(panel.label()) / 2f,
        0,
        0xffb8eee4,
        true,
        stack.last().pose(),
        textBuffers,
        Font.DisplayMode.NORMAL,
        0x33000000,
        LightTexture.FULL_BRIGHT);
    textBuffers.endBatch();
    stack.popPose();
  }
}
