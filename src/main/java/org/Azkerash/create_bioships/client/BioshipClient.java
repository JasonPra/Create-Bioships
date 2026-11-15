package org.Azkerash.create_bioships.client;

import org.Azkerash.create_bioships.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import dev.simulated_team.simulated.index.SimClickInteractions;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Create_bioships.MODID, value = Dist.CLIENT)
public final class BioshipClient {
    public static final LivingControlHandler CONTROLS = new LivingControlHandler();
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> SimClickInteractions.register(CONTROLS)); }
    public static void startControl(Level level, Player player, BlockPos pos) { CONTROLS.startHold(level, player, pos); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Create_bioships.TENTACLE_ENTITY.get(), FlagellumRenderer::new);
    }
}
