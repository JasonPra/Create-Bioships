package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public class BlueprintPanelBlock extends HorizontalDirectionalBlock {
  public static final MapCodec<BlueprintPanelBlock> CODEC = simpleCodec(BlueprintPanelBlock::new);

  public BlueprintPanelBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
  }

  @Override
  protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!AirshipPrototype.available()) return InteractionResult.PASS;
    if (hit.getDirection() != state.getValue(FACING)) return InteractionResult.PASS;
    if (player instanceof ServerPlayer server) {
      var core = BioshipAccess.core(level, pos);
      if (core == null
          || !player.getUUID().equals(core.creator())
          || core.body().dead()
          || !core.isMature()) return InteractionResult.FAIL;
      if (core.blueprint().busy()) {
        player.displayClientMessage(Component.literal(core.blueprint().status()), false);
        return InteractionResult.SUCCESS;
      }
      core.body().observe(core);
      server.openMenu(
          new SimpleMenuProvider(
              (id, inv, p) -> new BlueprintMenu(id, inv, core, pos),
              Component.literal("Living Blueprint")),
          b -> {
            b.writeBlockPos(pos);
            b.writeBlockPos(core.getBlockPos());
            b.writeNbt(core.body().save());
            b.writeNbt(core.blueprint().save());
          });
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
