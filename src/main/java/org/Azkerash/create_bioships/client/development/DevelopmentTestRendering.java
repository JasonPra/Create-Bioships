package org.Azkerash.create_bioships.client.development;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.development.testing.DevelopmentTestBlocks;

@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT)
public final class DevelopmentTestRendering {
  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () ->
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                DevelopmentTestBlocks.GLASS.get(),
                net.minecraft.client.renderer.RenderType.translucent()));
  }
}
