package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.Create_bioships;

/** Fixture-only blocks: deliberately no item registration or creative inventory entry. */
@EventBusSubscriber(modid = Create_bioships.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DevelopmentTestBlocks {
  public static final DeferredHolder<Block, TransparentBlock> GLASS =
      DeferredHolder.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_glass"));

  private DevelopmentTestBlocks() {}

  @SubscribeEvent
  public static void register(RegisterEvent event) {
    event.register(
        Registries.BLOCK,
        helper ->
            helper.register(
                GLASS.getId(),
                new TransparentBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                        .strength(-1, 3600000)
                        .noLootTable())));
  }

  @EventBusSubscriber(modid = Create_bioships.MODID)
  public static final class Protection {
    @SubscribeEvent
    public static void broken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
      if (event.getState().is(GLASS.get())) event.setCanceled(true);
    }
  }
}
