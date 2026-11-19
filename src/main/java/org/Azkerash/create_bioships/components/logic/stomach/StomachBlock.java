package org.Azkerash.create_bioships.components.logic.stomach;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** Internal organ with autonomous digestion and an inspect-only inventory. */
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
    return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
  }

  @Override
  public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
      net.minecraft.core.BlockPos pos, BlockState state) {
    return new StomachBlockEntity(pos, state);
  }

  @Override
  protected net.minecraft.world.ItemInteractionResult useItemOn(
      net.minecraft.world.item.ItemStack stack,
      BlockState state,
      net.minecraft.world.level.Level level,
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand,
      net.minecraft.world.phys.BlockHitResult hit) {
    if (stack.getItem() instanceof com.github.elenterius.biomancy.item.injector.InjectorItem
        && level.getBlockEntity(pos) instanceof StomachBlockEntity stomach)
      return stomach.genes().inject(player, stack);
    return stack.getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem
        ? net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected net.minecraft.world.InteractionResult useWithoutItem(
      BlockState state,
      net.minecraft.world.level.Level level,
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.phys.BlockHitResult hit) {
    if (hit.getDirection() != state.getValue(FACING))
      return net.minecraft.world.InteractionResult.PASS;
    if (player instanceof net.minecraft.server.level.ServerPlayer server
        && level.getBlockEntity(pos) instanceof StomachBlockEntity stomach)
      server.openMenu(stomach);
    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(
      BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
    var drops = super.getDrops(state, builder);
    var be =
        builder.getOptionalParameter(
            net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
    if (be instanceof StomachBlockEntity stomach)
      for (var stack : drops)
        if (stack.is(asItem())) stomach.saveToItem(stack, builder.getLevel().registryAccess());
    return drops;
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
