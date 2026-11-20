package org.azkerash.createbioships.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** A framed bookcase; enchanting power is assigned through the vanilla block tag. */
public final class OssuaryBookcaseBlock extends HorizontalDirectionalBlock {
  public static final MapCodec<OssuaryBookcaseBlock> CODEC = simpleCodec(OssuaryBookcaseBlock::new);

  public OssuaryBookcaseBlock(Properties p) {
    super(p);
    registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<OssuaryBookcaseBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
  }

  @Override
  protected BlockState rotate(BlockState s, Rotation r) {
    return s.setValue(FACING, r.rotate(s.getValue(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState s, Mirror m) {
    return rotate(s, m.getRotation(s.getValue(FACING)));
  }
}
