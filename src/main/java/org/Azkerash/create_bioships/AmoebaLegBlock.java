package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** A retractile locomotion socket. Facing points away from its supporting body face. */
public final class AmoebaLegBlock extends DirectionalBlock {
  public static final MapCodec<AmoebaLegBlock> CODEC = simpleCodec(AmoebaLegBlock::new);

  public AmoebaLegBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
  }

  static Direction outward(LivingCoreBlockEntity core, BlockPos target) {
    return outward(BioshipStructure.cells(core.ship()), target);
  }

  static Direction outward(java.util.Map<BlockPos, BlockState> cells, BlockPos target) {
    for (var face : Direction.values()) {
      var host = cells.get(target.relative(face.getOpposite()));
      if (host != null
          && !BioshipLegEvolution.leg(host)
          && !host.is(Create_bioships.SURFACE_EYE.get())) return face;
    }
    return Direction.DOWN;
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
      case DOWN -> Block.box(4, 12, 4, 12, 16, 12);
      case UP -> Block.box(4, 0, 4, 12, 4, 12);
      case NORTH -> Block.box(4, 4, 12, 12, 12, 16);
      case SOUTH -> Block.box(4, 4, 0, 12, 12, 4);
      case WEST -> Block.box(12, 4, 4, 16, 12, 12);
      case EAST -> Block.box(0, 4, 4, 4, 12, 12);
    };
  }
}
