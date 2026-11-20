package org.azkerash.createbioships.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import org.azkerash.createbioships.components.logic.WaterloggedComponentBlock;

/** Vanilla book storage, reading and comparator behavior on an ossified reading stand. */
public final class OssuaryLecternBlock extends LecternBlock implements SimpleWaterloggedBlock {
  public static final MapCodec<LecternBlock> CODEC = simpleCodec(OssuaryLecternBlock::new);

  public OssuaryLecternBlock(Properties p) {
    super(p);
    registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
  }

  @Override
  public MapCodec<LecternBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.WATERLOGGED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return WaterloggedComponentBlock.placed(super.getStateForPlacement(c), c);
  }

  @Override
  protected FluidState getFluidState(BlockState s) {
    return WaterloggedComponentBlock.fluid(s);
  }

  @Override
  protected BlockState updateShape(
      BlockState s, Direction d, BlockState other, LevelAccessor l, BlockPos p, BlockPos q) {
    WaterloggedComponentBlock.schedule(s, l, p);
    return super.updateShape(s, d, other, l, p, q);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new ReadingStandEntity(p, s);
  }

  public static final class ReadingStandEntity extends LecternBlockEntity {
    public ReadingStandEntity(BlockPos p, BlockState s) {
      super(p, s);
    }

    @Override
    public BlockEntityType<?> getType() {
      return FurnitureRegistry.READING_STAND_ENTITY.get();
    }
  }
}
