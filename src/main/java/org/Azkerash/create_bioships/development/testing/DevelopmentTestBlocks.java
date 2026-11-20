package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.registration.BlockRegistration;

/** Fixture-only blocks: deliberately no item registration or creative inventory entry. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class DevelopmentTestBlocks {
  // Minecraft uses negative hardness for unbreakable blocks; fixture blast resistance is separate.
  private static final float UNBREAKABLE_HARDNESS = -1;
  private static final float FIXTURE_BLAST_RESISTANCE = 3_600_000;
  private static final int BUTTON_PRESS_TICKS = 20;
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

  public static final DeferredHolder<Block, SlabBlock> PATH_SLAB =
      DeferredHolder.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_path_slab"));

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

  private static final java.util.Map<String, DeferredHolder<Block, Block>> FLOOR =
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
            "mossy_cobblestone")) {
      FLOOR.put(
          name,
          DeferredHolder.create(
              Registries.BLOCK,
              ResourceLocation.fromNamespaceAndPath(Create_bioships.MODID, "development_" + name)));
    }
  }

  /** Preserve declaration order for seeded fixture palettes without exposing registry mutation. */
  public static java.util.Map<String, DeferredHolder<Block, Block>> getFloorBlocks() {
    return java.util.Collections.unmodifiableMap(FLOOR);
  }

  public static boolean protectedBlock(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(PATH.get())
        || state.is(PATH_SLAB.get())
        || state.is(GLASS.get())
        || state.is(GLASS_DOOR.get())
        || state.is(BUTTON.get())
        || FLOOR.values().stream().anyMatch(h -> state.is(h.get()));
  }

  private DevelopmentTestBlocks() {}

  private static BlockBehaviour.Properties fixtureProperties(Block source) {
    return BlockBehaviour.Properties.ofFullCopy(source)
        .strength(UNBREAKABLE_HARDNESS, FIXTURE_BLAST_RESISTANCE)
        .noLootTable();
  }

  @SubscribeEvent
  public static void register(RegisterEvent event) {
    event.register(
        Registries.BLOCK,
        helper -> {
          BlockRegistration.register(
              helper, PATH_SLAB, () -> new SlabBlock(fixtureProperties(Blocks.STONE_SLAB)));
          BlockRegistration.register(
              helper, PATH, () -> new DevelopmentPathBlock(fixtureProperties(Blocks.DIRT_PATH)));
          BlockRegistration.register(
              helper,
              GLASS_DOOR,
              () ->
                  new DoorBlock(
                      net.minecraft.world.level.block.state.properties.BlockSetType.OAK,
                      fixtureProperties(Blocks.OAK_DOOR).noOcclusion()));
          BlockRegistration.register(
              helper,
              BUTTON,
              () ->
                  new ButtonBlock(
                      net.minecraft.world.level.block.state.properties.BlockSetType.STONE,
                      BUTTON_PRESS_TICKS,
                      fixtureProperties(Blocks.STONE_BUTTON)));
          FLOOR.forEach(
              (name, holder) ->
                  BlockRegistration.register(
                      helper,
                      holder,
                      () ->
                          new Block(
                              fixtureProperties(
                                  net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                                      ResourceLocation.withDefaultNamespace(name))))));
        });
    event.register(
        Registries.BLOCK,
        helper ->
            BlockRegistration.register(
                helper, GLASS, () -> new TransparentBlock(fixtureProperties(Blocks.GLASS))));
  }

  @EventBusSubscriber(modid = Create_bioships.MODID)
  public static final class Protection {
    @SubscribeEvent
    public static void broken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
      if (protectedBlock(event.getState())) {
        event.setCanceled(true);
      }
    }
  }
}
