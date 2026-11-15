package org.Azkerash.create_bioships;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class IntestineBlock extends Block {
  public static final BooleanProperty ENCASED = BooleanProperty.create("encased");

  public IntestineBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(ENCASED, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ENCASED);
  }
}
