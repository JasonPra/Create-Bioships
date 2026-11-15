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
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty
      WINDOW_MOUNTED =
          net.minecraft.world.level.block.state.properties.BooleanProperty.create("window_mounted");
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty
      WINDOW_INTERNAL =
          net.minecraft.world.level.block.state.properties.BooleanProperty.create(
              "window_internal");
  public static final MapCodec<SurfaceEyeBlock> CODEC = simpleCodec(SurfaceEyeBlock::new);

  public SurfaceEyeBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WINDOW_MOUNTED, false)
            .setValue(WINDOW_INTERNAL, false));
  }

  @Override
  protected MapCodec<? extends DirectionalBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, WINDOW_MOUNTED, WINDOW_INTERNAL);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return mounted(
        defaultBlockState().setValue(FACING, context.getClickedFace()),
        context.getLevel(),
        context.getClickedPos());
  }

  static BlockState mounted(BlockState state, LevelAccessor level, BlockPos pos) {
    var host = level.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
    boolean window = host.is(Create_bioships.ONE_WAY_WINDOW.get());
    return state
        .setValue(WINDOW_MOUNTED, window)
        .setValue(
            WINDOW_INTERNAL,
            window
                && state.getValue(FACING) == host.getValue(OneWayWindowBlock.FACING).getOpposite());
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      Direction side,
      BlockState neighbor,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    return mounted(state, level, pos);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
    super.onPlace(state, level, pos, previous, moved);
    var next = mounted(state, level, pos);
    if (!next.equals(state)) level.setBlock(pos, next, 2);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    if (state.getValue(WINDOW_INTERNAL)) return Shapes.empty();
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
