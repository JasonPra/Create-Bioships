package org.azkerash.createbioships.client.equipment;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.*;
import org.azkerash.createbioships.CreateBioships;

/** Reuse the complete native Positioner mesh; interactions remain restricted by our item. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class LimbPositionerRendering {
  @SubscribeEvent
  public static void register(RegisterClientExtensionsEvent event) {
    event.registerItem(
        new IClientItemExtensions() {
          private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

          @Override
          public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
              renderer = new dev.letitwalk.client.PositionerRenderer();
            }
            return renderer;
          }
        },
        CreateBioships.LIMB_POSITIONER.get());
  }
}
