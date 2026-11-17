package org.Azkerash.create_bioships;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;

/** The fresh prototype registers only its core block and matching item. */
@Mod(Create_bioships.MODID)
public final class Create_bioships {
  public static final String MODID = "create_bioships";
  private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
  private static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
  public static final DeferredBlock<CoreBlock> CORE =
      BLOCKS.register(
          "living_core",
          () -> new CoreBlock(BlockBehaviour.Properties.of().strength(2).noLootTable()));
  public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);

  static {
    TABS.register(
        "core",
        () ->
            CreativeModeTab.builder()
                .title(Component.literal("Bioship Core"))
                .icon(() -> CORE_ITEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> output.accept(CORE_ITEM.get()))
                .build());
  }

  public Create_bioships(IEventBus bus) {
    bus.addListener(
        (net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(BlockPlacementRules::refresh));
    BLOCKS.register(bus);
    ITEMS.register(bus);
    TABS.register(bus);
  }
}
