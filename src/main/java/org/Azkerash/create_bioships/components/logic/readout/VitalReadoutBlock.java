package org.Azkerash.create_bioships.components.logic.readout;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.logic.core.CoreGogglesDisplay;

/** A mounted inspection surface. All data and presentation are shared with the core. */
public final class VitalReadoutBlock
    extends org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock
    implements EntityBlock {
  public static final MapCodec<VitalReadoutBlock> CODEC = simpleCodec(VitalReadoutBlock::new);

  public VitalReadoutBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(BlockStateProperties.FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return placed(
        defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace()),
        context);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(BlockStateProperties.FACING)) {
      case NORTH -> box(2, 2, 13, 14, 14, 16);
      case SOUTH -> box(2, 2, 0, 14, 14, 3);
      case EAST -> box(0, 2, 2, 3, 14, 14);
      case WEST -> box(13, 2, 2, 16, 14, 14);
      case UP -> box(2, 0, 2, 14, 3, 14);
      case DOWN -> box(2, 13, 2, 14, 16, 14);
    };
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CoreGogglesDisplay(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return CoreGogglesDisplay.ticker(level, type);
  }
}
