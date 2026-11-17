package org.Azkerash.create_bioships.client;

import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.Azkerash.create_bioships.BioshipStructure;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.ChunkPos;
import org.Azkerash.create_bioships.*;

/** The vanilla red hurt overlay follows each actual body model in the core's local render frame. */
final class BioshipHurtFlash {
  static int overlay(net.minecraft.world.level.block.entity.BlockEntity entity, int fallback) {
    if (entity.getLevel() == null) return fallback;
    var container = SubLevelContainer.getContainer(entity.getLevel());
    var plot = container == null ? null : container.getPlot(new ChunkPos(entity.getBlockPos()));
    if (plot == null) return fallback;
    for (var actor : plot.getSubLevel().getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core && core.body().flashing(core))
        return OverlayTexture.pack(0, OverlayTexture.RED_OVERLAY_V);
    return fallback;
  }

  static void render(
      LivingCoreBlockEntity core, PoseStack pose, MultiBufferSource buffers, int light) {
    if (!core.body().flashing(core) || core.getLevel() == null) return;
    var container = SubLevelContainer.getContainer(core.getLevel());
    var plot = container == null ? null : container.getPlot(new ChunkPos(core.getBlockPos()));
    if (plot == null) return;
    var renderer = Minecraft.getInstance().getBlockRenderer();
    BioshipStructure.visit(
        plot.getSubLevel(),
        (pos, state) -> {
          pose.pushPose();
          var delta = pos.subtract(core.getBlockPos());
          pose.translate(delta.getX() - .001, delta.getY() - .001, delta.getZ() - .001);
          pose.scale(1.002f, 1.002f, 1.002f);
          renderer.renderSingleBlock(
              state,
              pose,
              buffers,
              light,
              OverlayTexture.pack(0, OverlayTexture.RED_OVERLAY_V),
              net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
              Sheets.translucentCullBlockSheet());
          pose.popPose();
        });
  }
}
