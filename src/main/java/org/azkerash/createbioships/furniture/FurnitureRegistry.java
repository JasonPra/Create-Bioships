package org.azkerash.createbioships.furniture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.registration.BlockRegistration;

/** Craftable furniture is separate from the living component simulation. */
public final class FurnitureRegistry {
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks(CreateBioships.MODID);
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems(CreateBioships.MODID);
  private static final List<DeferredItem<BlockItem>> DISPLAY_ITEMS = new ArrayList<>();
  public static final DeferredBlock<OssuaryTableBlock> OSSUARY_TABLE =
      block("ossuary_table", OssuaryTableBlock::new);

  private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateBioships.MODID);
  public static final DeferredBlock<OssuaryChairBlock> OSSUARY_CHAIR =
      block("ossuary_chair", OssuaryChairBlock::new);
  public static final DeferredBlock<OssuaryLecternBlock> OSSUARY_LECTERN =
      block("ossuary_lectern", OssuaryLecternBlock::new);
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<OssuaryLecternBlock.ReadingStandEntity>>
      READING_STAND_ENTITY =
          ENTITIES.register(
              "ossuary_lectern",
              () ->
                  BlockEntityType.Builder.of(
                          OssuaryLecternBlock.ReadingStandEntity::new, OSSUARY_LECTERN.get())
                      .build(null));

  public static final DeferredBlock<OssuaryBookcaseBlock> OSSUARY_BOOKCASE =
      block("ossuary_bookcase", OssuaryBookcaseBlock::new);
  public static final DeferredBlock<OssuaryArchiveBlock> OSSUARY_ARCHIVE =
      block("ossuary_archive", OssuaryArchiveBlock::new);
  public static final DeferredBlock<OssuaryCabinetBlock> OSSUARY_CABINET =
      block("ossuary_cabinet", OssuaryCabinetBlock::new);
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<OssuaryArchiveBlock.ArchiveEntity>>
      ARCHIVE_ENTITY =
          ENTITIES.register(
              "ossuary_archive",
              () ->
                  BlockEntityType.Builder.of(
                          OssuaryArchiveBlock.ArchiveEntity::new, OSSUARY_ARCHIVE.get())
                      .build(null));
  public static final DeferredHolder<
          BlockEntityType<?>, BlockEntityType<OssuaryCabinetBlock.CabinetEntity>>
      CABINET_ENTITY =
          ENTITIES.register(
              "ossuary_cabinet",
              () ->
                  BlockEntityType.Builder.of(
                          OssuaryCabinetBlock.CabinetEntity::new, OSSUARY_CABINET.get())
                      .build(null));

  public static final DeferredBlock<OssuaryBedBlock> OSSUARY_BED =
      block("ossuary_bed", OssuaryBedBlock::new);
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OssuaryBedBlock.BedEntity>>
      BED_ENTITY =
          ENTITIES.register(
              "ossuary_bed",
              () ->
                  BlockEntityType.Builder.of(OssuaryBedBlock.BedEntity::new, OSSUARY_BED.get())
                      .build(null));

  private FurnitureRegistry() {}

  static BlockBehaviour.Properties properties() {
    return BlockBehaviour.Properties.of().strength(2).sound(SoundType.BONE_BLOCK).noOcclusion();
  }

  private static <T extends Block> DeferredBlock<T> block(
      String id, Function<BlockBehaviour.Properties, T> constructor) {
    var registered =
        BlockRegistration.register(
            BLOCKS,
            ITEMS,
            id,
            BlockRegistration.createFactory(constructor, FurnitureRegistry::properties));
    DISPLAY_ITEMS.add(registered.item());
    return registered.block();
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
