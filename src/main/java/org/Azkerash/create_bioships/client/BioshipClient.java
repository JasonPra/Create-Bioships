package org.Azkerash.create_bioships.client;

import dev.simulated_team.simulated.index.SimClickInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.Azkerash.create_bioships.*;

@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class BioshipClient {
  public static final LivingControlHandler CONTROLS = new LivingControlHandler();

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> SimClickInteractions.register(CONTROLS));
  }

  public static void startControl(Level level, Player player, BlockPos pos) {
    CONTROLS.startHold(level, player, pos);
  }

  @SubscribeEvent
  public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
    event.register(Create_bioships.STOMACH_MENU.get(), StomachScreen::new);
    event.register(Create_bioships.BLUEPRINT_MENU.get(), BlueprintScreen::new);
    event.register(Create_bioships.ACCESS_MENU.get(), AccessScreen::new);
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(
        Create_bioships.BIOSHIP_TARGET.get(),
        net.minecraft.client.renderer.entity.NoopRenderer::new);
    event.registerBlockEntityRenderer(
        Create_bioships.CORE_ENTITY.get(), AssimilationArmRenderer::new);
    event.registerBlockEntityRenderer(
        Create_bioships.TENTACLE_ENTITY.get(), FlagellumRenderer::new);
  }
}
