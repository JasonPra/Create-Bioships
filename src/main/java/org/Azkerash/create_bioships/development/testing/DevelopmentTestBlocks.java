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
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class DevelopmentTestBlocks {
  public static final DeferredHolder<Block, TransparentBlock> GLASS =
      DeferredHolder.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_glass"));

  public static final DeferredHolder<Block, DoorBlock> GLASS_DOOR =
      DeferredHolder.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_glass_door"));

  public static final DeferredHolder<Block, DevelopmentPathBlock> PATH =
      DeferredHolder.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_dirt_path"));

  public static final class DevelopmentPathBlock extends DirtPathBlock {
    public DevelopmentPathBlock(BlockBehaviour.Properties properties) {
      super(properties);
    }

    @Override
    protected net.minecraft.world.level.block.state.BlockState updateShape(
        net.minecraft.world.level.block.state.BlockState state,
        net.minecraft.core.Direction direction,
        net.minecraft.world.level.block.state.BlockState other,
        net.minecraft.world.level.LevelAccessor level,
        net.minecraft.core.BlockPos pos,
        net.minecraft.core.BlockPos otherPos) {
      return state;
    }
  }

  public static final java.util.Map<String, DeferredHolder<Block, Block>> FLOOR =
      new java.util.LinkedHashMap<>();
  public static final DeferredHolder<Block, Block> BUTTON =
      DeferredHolder.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_stone_button"));

  static {
    for (var name :
        java.util.List.of(
            "stone_bricks",
            "mossy_stone_bricks",
            "cracked_stone_bricks",
            "cobblestone",
            "mossy_cobblestone"))
      FLOOR.put(
          name,
          DeferredHolder.create(
              Registries.BLOCK,
              ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_" + name)));
  }

  public static boolean protectedBlock(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(PATH.get())
        || state.is(GLASS.get())
        || state.is(GLASS_DOOR.get())
        || state.is(BUTTON.get())
        || FLOOR.values().stream().anyMatch(h -> state.is(h.get()));
  }

  private DevelopmentTestBlocks() {}

  @SubscribeEvent
  public static void register(RegisterEvent event) {
    event.register(
        Registries.BLOCK,
        helper -> {
          helper.register(
              PATH.getId(),
              new DevelopmentPathBlock(
                  BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT_PATH)
                      .strength(-1, 3600000)
                      .noLootTable()));
          helper.register(
              GLASS_DOOR.getId(),
              new DoorBlock(
                  net.minecraft.world.level.block.state.properties.BlockSetType.OAK,
                  BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR)
                      .strength(-1, 3600000)
                      .noLootTable()
                      .noOcclusion()));
          helper.register(
              BUTTON.getId(),
              new ButtonBlock(
                  net.minecraft.world.level.block.state.properties.BlockSetType.STONE,
                  20,
                  BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BUTTON)
                      .strength(-1, 3600000)
                      .noLootTable()));
          FLOOR.forEach(
              (name, holder) ->
                  helper.register(
                      holder.getId(),
                      new Block(
                          BlockBehaviour.Properties.ofFullCopy(
                                  net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                                      ResourceLocation.withDefaultNamespace(name)))
                              .strength(-1, 3600000)
                              .noLootTable())));
        });
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
      if (protectedBlock(event.getState())) event.setCanceled(true);
    }
  }
}
