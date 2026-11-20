package org.azkerash.createbioships.client.furniture;

import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.azkerash.createbioships.furniture.FurnitureRegistry;

@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class FurnitureRenderers {
  private FurnitureRenderers() {}

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers e) {
    e.registerBlockEntityRenderer(
        FurnitureRegistry.READING_STAND_ENTITY.get(), LecternRenderer::new);
  }
}
