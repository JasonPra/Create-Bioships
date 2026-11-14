package org.Azkerash.create_bioships.client;

import org.Azkerash.create_bioships.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BioshipClient {
    public static void openThrottle(BlockPos pos, int setting) { Minecraft.getInstance().setScreen(new ThrottleScreen(pos, setting)); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Create_bioships.TENTACLE_ENTITY.get(), FlagellumRenderer::new);
    }
}
