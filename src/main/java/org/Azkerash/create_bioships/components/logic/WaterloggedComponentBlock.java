package org.Azkerash.create_bioships.components.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;

/** Shared vanilla waterlogging for non-solid biological components, with no simulation ticker. */
public class WaterloggedComponentBlock extends Block implements SimpleWaterloggedBlock {
  public WaterloggedComponentBlock(Properties p) {
    super(p);
    registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, false));
  }

  public static BlockState placed(BlockState state, BlockPlaceContext context) {
    return state == null
        ? null
        : state.setValue(
            BlockStateProperties.WATERLOGGED,
            context
                .getLevel()
                .getFluidState(context.getClickedPos())
                .is(net.minecraft.tags.FluidTags.WATER));
  }

  public static FluidState fluid(BlockState state) {
    return state.getValue(BlockStateProperties.WATERLOGGED)
        ? Fluids.WATER.getSource(false)
        : Fluids.EMPTY.defaultFluidState();
  }

  public static void schedule(BlockState state, LevelAccessor level, BlockPos pos) {
    if (state.getValue(BlockStateProperties.WATERLOGGED)) {
      level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
    }
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.WATERLOGGED);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return placed(super.getStateForPlacement(c), c);
  }

  @Override
  protected FluidState getFluidState(BlockState s) {
    return fluid(s);
  }

  @Override
  protected BlockState updateShape(
      BlockState s,
      net.minecraft.core.Direction d,
      BlockState other,
      LevelAccessor l,
      BlockPos p,
      BlockPos q) {
    schedule(s, l, p);
    return super.updateShape(s, d, other, l, p, q);
  }
}
