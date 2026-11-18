package org.Azkerash.create_bioships.components.logic.stomach;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/**
 * Internal organ: one capacity bonus per creature and an empty inspection hook; no digestion or
 * GUI.
 */
public final class StomachBlock extends DirectionalBlock
    implements com.simibubi.create.content.equipment.wrench.IWrenchable,
        net.minecraft.world.level.block.EntityBlock {
  public static final MapCodec<StomachBlock> CODEC = simpleCodec(StomachBlock::new);

  public StomachBlock(Properties properties) {
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
    if (context.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
      var body =
          org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(
              server, context.getClickedPos());
      if (body != null
          && org.Azkerash.create_bioships.core.CoreAssemblyRules.isCreature(body)
          && !server.getBlockState(context.getClickedPos()).is(this)
          && org.Azkerash.create_bioships.core.SubLevelHunger.hasStomach(body)) return null;
    }
    return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
  }

  @Override
  public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
      net.minecraft.core.BlockPos pos, BlockState state) {
    return new StomachGogglesDisplay(pos, state);
  }

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    return state.rotate(mirror.getRotation(state.getValue(FACING)));
  }
}
