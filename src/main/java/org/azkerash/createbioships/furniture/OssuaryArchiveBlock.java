package org.azkerash.createbioships.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Six real book slots with native insertion, removal and comparator behavior. */
public final class OssuaryArchiveBlock extends ChiseledBookShelfBlock {
  public static final MapCodec<ChiseledBookShelfBlock> CODEC =
      simpleCodec(OssuaryArchiveBlock::new);

  public OssuaryArchiveBlock(Properties p) {
    super(p);
  }

  @Override
  public MapCodec<ChiseledBookShelfBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new ArchiveEntity(p, s);
  }

  public static final class ArchiveEntity extends ChiseledBookShelfBlockEntity {
    public ArchiveEntity(BlockPos p, BlockState s) {
      super(p, s);
    }

    @Override
    public BlockEntityType<?> getType() {
      return FurnitureRegistry.ARCHIVE_ENTITY.get();
    }
  }
}
