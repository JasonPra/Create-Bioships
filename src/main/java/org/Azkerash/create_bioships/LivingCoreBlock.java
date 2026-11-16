package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class LivingCoreBlock extends BaseEntityBlock
    implements dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener {
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("active");
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty BLINK =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("blink");
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty RELOCATING =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("relocating");
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty CONCEALED =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("concealed");
  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
  public static final MapCodec<LivingCoreBlock> CODEC = simpleCodec(LivingCoreBlock::new);

  public LivingCoreBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(ACTIVE, false)
            .setValue(BLINK, false)
            .setValue(RELOCATING, false)
            .setValue(CONCEALED, false)
            .setValue(FACING, net.minecraft.core.Direction.NORTH));
  }

  @Override
  protected void createBlockStateDefinition(
      net.minecraft.world.level.block.state.StateDefinition.Builder<
              net.minecraft.world.level.block.Block, BlockState>
          builder) {
    builder.add(ACTIVE, BLINK, RELOCATING, CONCEALED, FACING);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockState getStateForPlacement(
      net.minecraft.world.item.context.BlockPlaceContext context) {
    var container =
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(context.getLevel());
    if (container != null) {
      var pos = context.getClickedPos();
      for (var point : java.util.List.of(pos, pos.relative(context.getClickedFace().getOpposite())))
        if (container.inBounds(point)
            && container.getPlot(new net.minecraft.world.level.ChunkPos(point)) != null)
          return null;
    }
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  public void setPlacedBy(
      Level level,
      BlockPos pos,
      BlockState state,
      net.minecraft.world.entity.LivingEntity placer,
      ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (placer instanceof Player player
        && level.getBlockEntity(pos) instanceof LivingCoreBlockEntity core)
      core.claimCreator(player.getUUID());
    if (level instanceof net.minecraft.server.level.ServerLevel server)
      BioshipIntegration.awaken(server, pos);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
    super.onPlace(state, level, pos, oldState, moved);
    if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 1);
  }

  @Override
  protected void tick(
      BlockState state,
      net.minecraft.server.level.ServerLevel level,
      BlockPos pos,
      net.minecraft.util.RandomSource random) {
    BioshipIntegration.awaken(level, pos);
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
    if (!next.is(this)
        && !level.isClientSide
        && level.getBlockEntity(pos) instanceof LivingCoreBlockEntity core) core.coreRemoved();
    super.onRemove(state, level, pos, next, moved);
  }

  @Override
  public void beforeMove(
      net.minecraft.server.level.ServerLevel source,
      net.minecraft.server.level.ServerLevel destination,
      BlockState state,
      BlockPos from,
      BlockPos to) {
    if (source.getBlockEntity(from) instanceof LivingCoreBlockEntity core)
      core.recordTransfer(destination, to);
  }

  @Override
  public void afterMove(
      net.minecraft.server.level.ServerLevel source,
      net.minecraft.server.level.ServerLevel destination,
      BlockState state,
      BlockPos from,
      BlockPos to) {
    // The source keeps its transfer receipt until removal; it is never serialized onto the new
    // core.
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new LivingCoreBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(type, Create_bioships.CORE_ENTITY.get(), LivingCoreBlockEntity::tick);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof LivingCoreBlockEntity core)
      core.interact(player, stack);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof LivingCoreBlockEntity core)
      core.interact(player, ItemStack.EMPTY);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public void attack(BlockState state, Level level, BlockPos pos, Player player) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof LivingCoreBlockEntity core)
      core.body().struck(core, player);
  }

  @Override
  protected java.util.List<ItemStack> getDrops(
      BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
    return BioshipContainerState.packDrops(super.getDrops(state, params), params);
  }

  @Override
  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    BioshipContainerState.creativeBreak(level, pos, player);
    return super.playerWillDestroy(level, pos, state, player);
  }
}
