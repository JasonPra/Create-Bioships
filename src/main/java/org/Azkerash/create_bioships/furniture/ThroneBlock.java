package org.Azkerash.create_bioships.furniture;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Two-block throne furniture, using Create's native sitting entity rather than another seat system.
 */
public final class ThroneBlock extends SeatBlock {
  public static final MapCodec<ThroneBlock> CODEC = simpleCodec(ThroneBlock::new);
  private static final VoxelShape[][] SHAPES = shapes();

  public ThroneBlock(Properties properties) {
    super(properties, DyeColor.RED);
    registerDefaultState(
        defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.DOUBLE_BLOCK_HALF);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    if (c.getClickedPos().getY() >= c.getLevel().getMaxBuildHeight() - 1
        || !c.getLevel().getBlockState(c.getClickedPos().above()).canBeReplaced(c)) {
      return null;
    }
    var state =
        withWater(
            defaultBlockState()
                .setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    c.getHorizontalDirection().getOpposite()),
            c);
    return state.canSurvive(c.getLevel(), c.getClickedPos()) ? state : null;
  }

  @Override
  public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity placer, ItemStack stack) {
    l.setBlock(
        p.above(),
        s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
            .setValue(
                BlockStateProperties.WATERLOGGED,
                l.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER)),
        3);
  }

  @Override
  protected boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
    if (s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
      var lower = l.getBlockState(p.below());
      return lower.is(this)
          && lower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }
    return l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP);
  }

  @Override
  public BlockState updateShape(
      BlockState s, Direction d, BlockState other, LevelAccessor l, BlockPos p, BlockPos q) {
    super.updateShape(s, d, other, l, p, q);
    var half = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
    boolean counterpart = d == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
    if ((counterpart
            && (!other.is(this) || other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == half))
        || !s.canSurvive(l, p)) {
      return s.getValue(BlockStateProperties.WATERLOGGED)
          ? Fluids.WATER.defaultFluidState().createLegacyBlock()
          : Blocks.AIR.defaultBlockState();
    }
    return s;
  }

  @Override
  public BlockState playerWillDestroy(Level l, BlockPos p, BlockState s, Player player) {
    if (!l.isClientSide
        && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
        && l.getBlockState(p.below()).is(this)) {
      l.destroyBlock(p.below(), !player.isCreative(), player);
    }
    return super.playerWillDestroy(l, p, s, player);
  }

  @Override
  public BlockState rotate(BlockState s, Rotation r) {
    return s.setValue(
        BlockStateProperties.HORIZONTAL_FACING,
        r.rotate(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  @Override
  public BlockState mirror(BlockState s, Mirror m) {
    return s.rotate(m.getRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  private InteractionResult sit(BlockState state, Level l, BlockPos p, Player player) {
    if (player.isShiftKeyDown() || player.isSpectator()) {
      return InteractionResult.PASS;
    }
    if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
      p = p.below();
    }
    if (!l.isClientSide) {
      if (SeatBlock.isSeatOccupied(l, p)) {
        return InteractionResult.FAIL;
      }
      SeatBlock.sitDown(l, p, player);
    }
    return InteractionResult.sidedSuccess(l.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
    return sit(s, l, p, player);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState s,
      Level l,
      BlockPos p,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    // Dye must not turn the throne into an ordinary Create seat or strand its upper half.
    var result = sit(s, l, p, player);
    return result.consumesAction()
        ? ItemInteractionResult.SUCCESS
        : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  public void updateEntityAfterFallOn(BlockGetter l, Entity entity) {
    // Furniture does not automatically capture mobs walking across it.
    if (entity.getDeltaMovement().y < 0) {
      entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0, 1));
    }
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return SHAPES[
        s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 1 : 0][
        s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue()];
  }

  @Override
  public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return getShape(s, l, p, c);
  }

  private static VoxelShape[][] shapes() {
    var result = new VoxelShape[2][4];
    result[0][0] =
        Shapes.or(
            box(0, 0, 0, 16, 4, 16),
            box(3, 4, 3, 13, 9, 15),
            box(1, 4, 0, 15, 16, 4),
            box(0, 8, 3, 3, 14, 15),
            box(13, 8, 3, 16, 14, 15));
    result[1][0] =
        Shapes.or(box(2, 0, 0, 14, 13, 4), box(0, 9, 0, 3, 16, 4), box(13, 9, 0, 16, 16, 4));
    for (int half = 0; half < 2; half++) {
      for (int turn = 1; turn < 4; turn++) {
        var shape = Shapes.empty();
        for (var a : result[half][turn - 1].toAabbs()) {
          shape =
              Shapes.or(shape, Shapes.box(1 - a.maxZ, a.minY, a.minX, 1 - a.minZ, a.maxY, a.maxX));
        }
        result[half][turn] = shape;
      }
    }
    return result;
  }
}
