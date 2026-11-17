package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.disabled.wip.EngineRoom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The ship's food inventory, distinct from Biomancy's crafting machines. */
public class FeedingMouthBlock extends BaseEntityBlock {
  public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
      net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
  public static final MapCodec<FeedingMouthBlock> CODEC = simpleCodec(FeedingMouthBlock::new);

  public FeedingMouthBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
  }

  @Override
  protected void createBlockStateDefinition(
      net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return CODEC;
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.ENTITYBLOCK_ANIMATED;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new FeedingMouthBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type, Create_bioships.FEEDING_MOUTH_ENTITY.get(), FeedingMouthBlockEntity::tick);
  }

  static Direction outward(LivingCoreBlockEntity core, BlockPos pos) {
    var delta = pos.subtract(core.getBlockPos());
    Direction preferred =
        core.isMature() && EngineRoom.inside(core, pos)
            ? (delta.getX() > 0 ? Direction.WEST : Direction.EAST)
            : Math.abs(delta.getX()) > Math.abs(delta.getZ())
                ? (delta.getX() > 0 ? Direction.EAST : Direction.WEST)
                : delta.getZ() > 0 ? Direction.SOUTH : Direction.NORTH;
    var directions =
        new java.util.ArrayList<>(
            java.util.List.of(
                preferred,
                Direction.UP,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST,
                Direction.DOWN));
    for (var facing : directions) if (openFace(core, pos, facing)) return facing;
    return preferred;
  }

  static boolean openFace(LivingCoreBlockEntity core, BlockPos pos, Direction direction) {
    var front = pos.relative(direction);
    var level = core.getLevel();
    if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) return false;
    var physical =
        BlockPos.containing(
            core.ship()
                .logicalPose()
                .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(front)));
    // Water is habitat, not a solid obstruction. Respiration checks air access separately.
    return level.getBlockState(physical).getCollisionShape(level, physical).isEmpty();
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (hit.getDirection() != state.getValue(FACING)) return InteractionResult.PASS;
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof FeedingMouthBlockEntity mouth)
      player.openMenu(mouth);
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
