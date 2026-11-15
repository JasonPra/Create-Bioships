package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Steady cabin lamp. The legacy ID and lit property remain compatible with saved ships. */
public class PulseLightBlock extends Block {
  public static final BooleanProperty LIT = BooleanProperty.create("lit");

  public PulseLightBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(LIT, true));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(LIT);
  }

  @Override
  protected net.minecraft.world.phys.shapes.VoxelShape getShape(
      BlockState state,
      net.minecraft.world.level.BlockGetter level,
      BlockPos pos,
      net.minecraft.world.phys.shapes.CollisionContext context) {
    return Block.box(4, 10, 4, 12, 16, 12);
  }
}
