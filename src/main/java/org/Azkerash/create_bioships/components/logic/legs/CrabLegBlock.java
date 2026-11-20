package org.Azkerash.create_bioships.components.logic.legs;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.legs.SubLevelLegs;

/**
 * Passive horizontal socket. The owning grid connects generated limb sublevels to native Let It
 * Walk controls.
 */
public final class CrabLegBlock
    extends org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock
    implements BlockSubLevelAssemblyListener,
        com.simibubi.create.content.equipment.wrench.IWrenchable {
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty HEIGHT =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create("height", 1, 3);
  public static final MapCodec<CrabLegBlock> CODEC = simpleCodec(CrabLegBlock::new);

  public static void initialize(CrabLegBlock block) {
    org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.registerBounds(
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block),
        SubLevelLegs::collisionBounds);
    org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.registerStepHeight(
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block),
        SubLevelLegs::stepHeight);
  }

  public CrabLegBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .setValue(HEIGHT, 1));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(BlockStateProperties.HORIZONTAL_FACING, HEIGHT);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    var face = context.getClickedFace();
    if (!face.getAxis().isHorizontal()) {
      return null;
    }
    var state =
        placed(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face), context);
    return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
  }

  @Override
  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    var face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    var behind = pos.relative(face.getOpposite());
    var support = level.getBlockState(behind);
    return !support.is(ComponentRegistry.CORE.get())
        && !(support.getBlock() instanceof CrabLegBlock)
        && support.isCollisionShapeFullBlock(level, behind)
        && support.isFaceSturdy(level, behind, face);
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState other,
      LevelAccessor level,
      BlockPos pos,
      BlockPos otherPos) {
    if (!state.canSurvive(level, pos)) {
      level.scheduleTick(pos, this, 1);
    }
    return super.updateShape(state, direction, other, level, pos, otherPos);
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!state.canSurvive(level, pos)) {
      level.destroyBlock(pos, true);
    }
  }

  @Override
  public void afterMove(
      ServerLevel source, ServerLevel destination, BlockState state, BlockPos from, BlockPos to) {
    if (!state.canSurvive(destination, to)) {
      destination.scheduleTick(to, this, 1);
    }
    var body = org.Azkerash.create_bioships.core.CoreAssemblyRules.containing(destination, to);
    if (body != null) {
      SubLevelLegs.request(body);
    }
  }

  @Override
  public net.minecraft.world.InteractionResult onWrenched(
      BlockState state, net.minecraft.world.item.context.UseOnContext context) {
    if (!(context.getItemInHand().getItem()
        instanceof org.Azkerash.create_bioships.equipment.BioshipWrenchItem)) {
      return net.minecraft.world.InteractionResult.PASS;
    }
    var player = context.getPlayer();
    if (player == null || !player.mayBuild()) {
      return net.minecraft.world.InteractionResult.FAIL;
    }
    if (!context.getLevel().isClientSide && state.getValue(HEIGHT) < 3) {
      context
          .getLevel()
          .setBlock(
              context.getClickedPos(),
              state.setValue(HEIGHT, state.getValue(HEIGHT) + 1),
              Block.UPDATE_ALL);
      com.simibubi.create.content.equipment.wrench.IWrenchable.playRotateSound(
          context.getLevel(), context.getClickedPos());
    }
    return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
  }

  @Override
  public BlockState getRotatedBlockState(BlockState state, Direction face) {
    return state;
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
      case NORTH -> box(4, 4, 12, 12, 12, 16);
      case SOUTH -> box(4, 4, 0, 12, 12, 4);
      case EAST -> box(0, 4, 4, 4, 12, 12);
      case WEST -> box(12, 4, 4, 16, 12, 12);
      default -> Shapes.empty();
    };
  }
}
