package org.azkerash.createbioships.furniture;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Native Create sitting without dye replacement or automatic mob capture. */
public final class OssuaryChairBlock extends SeatBlock {
  public static final MapCodec<OssuaryChairBlock> CODEC = simpleCodec(OssuaryChairBlock::new);

  public OssuaryChairBlock(Properties p) {
    super(p, DyeColor.RED);
    registerDefaultState(
        defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.HORIZONTAL_FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return withWater(
        defaultBlockState()
            .setValue(
                BlockStateProperties.HORIZONTAL_FACING, c.getHorizontalDirection().getOpposite()),
        c);
  }

  @Override
  public BlockState rotate(BlockState s, Rotation r) {
    return s.setValue(
        BlockStateProperties.HORIZONTAL_FACING,
        r.rotate(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  @Override
  public BlockState mirror(BlockState s, Mirror m) {
    return rotate(s, m.getRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  private InteractionResult sit(Level l, BlockPos p, Player player) {
    if (player.isShiftKeyDown() || player.isSpectator()) {
      return InteractionResult.PASS;
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
    return sit(l, p, player);
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
    return sit(l, p, player).consumesAction()
        ? ItemInteractionResult.SUCCESS
        : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  public void updateEntityAfterFallOn(BlockGetter l, Entity entity) {
    if (entity.getDeltaMovement().y < 0) {
      entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0, 1));
    }
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    var shape = Shapes.or(box(2, 0, 2, 14, 9, 14), box(2, 9, 1, 14, 16, 4));
    int turns = s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue();
    for (int i = 0; i < turns; i++) {
      var rotated = Shapes.empty();
      for (var a : shape.toAabbs()) {
        rotated =
            Shapes.or(rotated, Shapes.box(1 - a.maxZ, a.minY, a.minX, 1 - a.minZ, a.maxY, a.maxX));
      }
      shape = rotated;
    }
    return shape;
  }

  @Override
  public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return getShape(s, l, p, c);
  }
}
