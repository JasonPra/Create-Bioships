package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

public class OneWayWindowBlock extends HorizontalDirectionalBlock {
  public static final MapCodec<OneWayWindowBlock> CODEC = simpleCodec(OneWayWindowBlock::new);

  public OneWayWindowBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
    if (adjacent.is(this)
        && adjacent.getValue(FACING) == state.getValue(FACING)
        && side.getAxis() != state.getValue(FACING).getAxis()) return true;
    return super.skipRendering(state, adjacent, side);
  }

  @Override
  protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }
}
