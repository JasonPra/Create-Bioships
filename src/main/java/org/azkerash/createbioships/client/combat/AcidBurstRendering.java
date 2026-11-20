package org.azkerash.createbioships.client.combat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.azkerash.createbioships.CreateBioships;

/** Droplets are client particles driven by RPL's synchronized burst positions. */
@EventBusSubscriber(modid = CreateBioships.MODID, value = Dist.CLIENT)
public final class AcidBurstRendering {
  private AcidBurstRendering() {}

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(
        CreateBioships.ACID_BURST.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
  }
}
