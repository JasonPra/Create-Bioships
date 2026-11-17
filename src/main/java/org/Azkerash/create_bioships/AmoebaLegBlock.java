package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
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

  public static Direction outward(LivingCoreBlockEntity core, BlockPos target) {
    return outward(BioshipStructure.cells(core.ship()), target);
  }

  static boolean supportsFace(BlockState host, BlockPos hostPos, Direction face) {
    return host != null
        && !BioshipLegEvolution.leg(host)
        && host.isFaceSturdy(EmptyBlockGetter.INSTANCE, hostPos, face);
  }

  /** Full structural faces only; sensory and respiratory openings remain unobstructed. */
  static boolean canHostLeg(BlockState host, BlockPos hostPos, Direction face) {
    return supportsFace(host, hostPos, face)
        && !host.is(Create_bioships.CORE.get())
        && !host.is(Create_bioships.LUNGS.get())
        && !(host.is(Create_bioships.FEEDING_MOUTH.get())
            && host.getValue(FeedingMouthBlock.FACING) == face)
        && !(host.is(Create_bioships.GILLS.get())
            && host.getValue(RespiratoryOrganBlock.FACING) == face);
  }

  public static boolean mounted(java.util.Map<BlockPos, BlockState> cells, BlockPos pos, BlockState leg) {
    var face = leg.getValue(FACING);
    var hostPos = pos.relative(face.getOpposite());
    return canHostLeg(cells.get(hostPos), hostPos, face);
  }

  public static Direction outward(java.util.Map<BlockPos, BlockState> cells, BlockPos target) {
    for (var face : Direction.values()) {
      var hostPos = target.relative(face.getOpposite());
      if (canHostLeg(cells.get(hostPos), hostPos, face)) return face;
    }
    return Direction.DOWN; // Callers must still validate mounted(); never fabricates support.
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
    var state = defaultBlockState().setValue(FACING, context.getClickedFace());
    return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
  }

  @Override
  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    var face = state.getValue(FACING);
    var hostPos = pos.relative(face.getOpposite());
    return canHostLeg(level.getBlockState(hostPos), hostPos, face);
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
