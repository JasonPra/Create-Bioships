package org.Azkerash.create_bioships.furniture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import org.Azkerash.create_bioships.Create_bioships;

/** Craftable furniture is separate from the living component simulation. */
public final class FurnitureRegistry {
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(Create_bioships.MODID);
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems(Create_bioships.MODID);
  private static final List<DeferredItem<BlockItem>> DISPLAY_ITEMS = new ArrayList<>();
  public static final DeferredBlock<OssuaryTableBlock> OSSUARY_TABLE =
      block("ossuary_table", () -> new OssuaryTableBlock(properties()));

  private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Create_bioships.MODID);
  public static final DeferredBlock<OssuaryChairBlock> OSSUARY_CHAIR =
      block("ossuary_chair", () -> new OssuaryChairBlock(properties()));
  public static final DeferredBlock<OssuaryLecternBlock> OSSUARY_LECTERN =
      block("ossuary_lectern", () -> new OssuaryLecternBlock(properties()));
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<OssuaryLecternBlock.ReadingStandEntity>>
      READING_STAND_ENTITY =
          ENTITIES.register(
              "ossuary_lectern",
              () ->
                  BlockEntityType.Builder.of(
                          OssuaryLecternBlock.ReadingStandEntity::new, OSSUARY_LECTERN.get())
                      .build(null));

  private FurnitureRegistry() {}

  static BlockBehaviour.Properties properties() {
    return BlockBehaviour.Properties.of().strength(2).sound(SoundType.BONE_BLOCK).noOcclusion();
  }

  private static <T extends Block> DeferredBlock<T> block(String id, Supplier<T> factory) {
    var block = BLOCKS.register(id, factory);
    DISPLAY_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
    return block;
  }

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    ENTITIES.register(bus);
  }

  public static void addToCreative(CreativeModeTab.Output output) {
    DISPLAY_ITEMS.forEach(i -> output.accept(i.get()));
  }
}
