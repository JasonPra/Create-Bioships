package org.Azkerash.create_bioships.client.components;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT)
public final class ComponentScreens {
  @SubscribeEvent
  public static void register(RegisterMenuScreensEvent event) {
    event.register(ComponentRegistry.STOMACH_MENU.get(), StomachScreen::new);
    event.register(ComponentRegistry.MOUTH_MENU.get(), MouthScreen::new);
    event.register(ComponentRegistry.BRAIN_MENU.get(), BrainScreen::new);
  }

  @SubscribeEvent
  public static void colors(
      net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
    event.register(
        (stack, tint) ->
            tint == 1
                    && stack.getItem()
                        instanceof com.github.elenterius.biomancy.api.serum.SerumContainer serum
                ? serum.getSerumColor(stack)
                : 0xffffffff,
        ComponentRegistry.LONGEVITY_SERUM.get(),
        ComponentRegistry.EFFICIENCY_SERUM.get());
  }
}
