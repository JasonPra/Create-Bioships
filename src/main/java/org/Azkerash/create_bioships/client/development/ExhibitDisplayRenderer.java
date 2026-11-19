package org.Azkerash.create_bioships.client.development;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draw exhibit text after opaque block entities, before glass, using vanilla text rendering. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class ExhibitDisplayRenderer {
  private static final List<Runnable> PENDING = new ArrayList<>();
  private static boolean replaying;

  private ExhibitDisplayRenderer() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static boolean defer(
      DisplayRenderer renderer,
      Display display,
      float yaw,
      float partial,
      PoseStack original,
      MultiBufferSource buffers,
      int light) {
    if (replaying
        || !(display instanceof Display.TextDisplay)
        || display.getTags().stream()
            .noneMatch(t -> t.startsWith("create_bioships:") || t.startsWith("create_bioships.")))
      return false;
    var pose = new PoseStack();
    pose.last().pose().set(original.last().pose());
    pose.last().normal().set(original.last().normal());
    PENDING.add(() -> renderer.render(display, yaw, partial, pose, buffers, light));
    return true;
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) PENDING.clear();
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
    replaying = true;
    try {
      for (var render : PENDING) render.run();
      Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    } finally {
      replaying = false;
      PENDING.clear();
    }
  }
}
