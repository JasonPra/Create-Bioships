package org.Azkerash.create_bioships.components.logic.nerve;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.core.*;

/** A physical terminal only. Permissions belong to the grid, never this block. */
public final class NervePanelBlock extends Block {
  public static final MapCodec<NervePanelBlock> CODEC = simpleCodec(NervePanelBlock::new);

  public NervePanelBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(BlockStateProperties.FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(BlockStateProperties.FACING)) {
      case NORTH -> box(1, 2, 11, 15, 14, 16);
      case SOUTH -> box(1, 2, 0, 15, 14, 5);
      case EAST -> box(0, 2, 1, 5, 14, 15);
      case WEST -> box(11, 2, 1, 16, 14, 15);
      case UP -> box(1, 0, 2, 15, 5, 14);
      case DOWN -> box(1, 11, 2, 15, 16, 14);
    };
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (hit.getDirection() != state.getValue(BlockStateProperties.FACING))
      return InteractionResult.PASS;
    if (player instanceof ServerPlayer server && level instanceof ServerLevel world) {
      var body = CoreAssemblyRules.containing(world, pos);
      if (body == null || !SubLevelData.isLiving(body))
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "Connect this panel to a creature sublevel."),
            true);
      else NervePanelControl.open(server, body, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
