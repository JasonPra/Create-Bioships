package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** A small sensory organ attached to a face of the organism's tissue. */
public final class SurfaceEyeBlock extends DirectionalBlock {
  public static final MapCodec<SurfaceEyeBlock> CODEC = simpleCodec(SurfaceEyeBlock::new);

  public SurfaceEyeBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<? extends DirectionalBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getClickedFace());
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(FACING)) {
      case NORTH -> Block.box(5, 5, 13, 11, 11, 16);
      case SOUTH -> Block.box(5, 5, 0, 11, 11, 3);
      case WEST -> Block.box(13, 5, 5, 16, 11, 11);
      case EAST -> Block.box(0, 5, 5, 3, 11, 11);
      case UP -> Block.box(5, 0, 5, 11, 3, 11);
      case DOWN -> Block.box(5, 13, 5, 11, 16, 11);
    };
  }
}
