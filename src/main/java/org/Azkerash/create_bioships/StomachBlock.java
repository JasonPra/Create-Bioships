package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.Azkerash.create_bioships.registry.Create_bioships;

public class StomachBlock extends BaseEntityBlock {
  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

  public enum Role implements net.minecraft.util.StringRepresentable {
    NORMAL,
    MEMBER,
    INTAKE,
    PANEL;

    public String getSerializedName() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  public static final net.minecraft.world.level.block.state.properties.EnumProperty<Role> ROLE =
      net.minecraft.world.level.block.state.properties.EnumProperty.create("role", Role.class);
  public static final MapCodec<StomachBlock> CODEC = simpleCodec(StomachBlock::new);

  public StomachBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(ROLE, Role.NORMAL));
  }

  @Override
  protected void createBlockStateDefinition(
      net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, ROLE);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new StomachBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(type, Create_bioships.STOMACH_ENTITY.get(), StomachBlockEntity::tick);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (player instanceof net.minecraft.server.level.ServerPlayer server
        && level.getBlockEntity(pos) instanceof StomachBlockEntity stomach)
      server.openMenu(stomach, buffer -> buffer.writeBlockPos(pos));
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(
      BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
    return BioshipContainerState.packDrops(super.getDrops(state, params), params);
  }

  @Override
  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    BioshipContainerState.creativeBreak(level, pos, player);
    return super.playerWillDestroy(level, pos, state, player);
  }
}
