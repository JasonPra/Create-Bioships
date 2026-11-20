package org.Azkerash.create_bioships.components.logic.eye;

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
import org.Azkerash.create_bioships.core.*;

/** Small outward-facing sensory component; land and aquatic forms share all behavior. */
@net.neoforged.fml.common.EventBusSubscriber(
    modid = org.Azkerash.create_bioships.Create_bioships.MODID)
public final class EyeBlock extends Block
    implements BlockSubLevelAssemblyListener, SimpleWaterloggedBlock {
  private static final java.util.Map<ServerLevel, java.util.Set<BlockPos>> SUPPORT_CHECKS =
      new java.util.WeakHashMap<>();

  public static void supportChanged(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body, BlockPos pos) {
    var pending = SUPPORT_CHECKS.computeIfAbsent(body.getLevel(), l -> new java.util.HashSet<>());
    pending.add(pos.immutable());
    for (var face : Direction.values()) {
      pending.add(pos.relative(face));
    }
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void validateSupports(
      net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      var pending = SUPPORT_CHECKS.remove(level);
      if (pending == null) {
        continue;
      }
      for (var pos : pending) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof EyeBlock && !state.canSurvive(level, pos)) {
          level.destroyBlock(pos, true);
        }
      }
    }
  }

  public static final MapCodec<EyeBlock> CODEC = simpleCodec(EyeBlock::new);

  public EyeBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        EyeBlink.sleeping(
            stateDefinition
                .any()
                .setValue(BlockStateProperties.WATERLOGGED, false)
                .setValue(BlockStateProperties.FACING, Direction.NORTH)));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(
        BlockStateProperties.WATERLOGGED,
        BlockStateProperties.FACING,
        EyeBlink.BLINK,
        EyeBlink.WAKE,
        EyeBlink.STAGE);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    var state =
        defaultBlockState()
            .setValue(BlockStateProperties.FACING, context.getClickedFace())
            .setValue(
                BlockStateProperties.WATERLOGGED,
                context
                    .getLevel()
                    .getFluidState(context.getClickedPos())
                    .is(net.minecraft.tags.FluidTags.WATER));
    return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
  }

  @Override
  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    return EyeWoundBehavior.hasSupport(state, level, pos);
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState other,
      LevelAccessor level,
      BlockPos pos,
      BlockPos otherPos) {
    if (state.getValue(BlockStateProperties.WATERLOGGED)) {
      level.scheduleTick(
          pos,
          net.minecraft.world.level.material.Fluids.WATER,
          net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
    }
    if (direction == state.getValue(BlockStateProperties.FACING).getOpposite()
        && !state.canSurvive(level, pos)) {
      level.scheduleTick(pos, this, 1);
    }
    return super.updateShape(state, direction, other, level, pos, otherPos);
  }

  @Override
  protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
    return state.getValue(BlockStateProperties.WATERLOGGED)
        ? net.minecraft.world.level.material.Fluids.WATER.getSource(false)
        : super.getFluidState(state);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (state.getValue(BlockStateProperties.FACING)) {
      case NORTH -> box(5, 5, 12, 11, 11, 16);
      case SOUTH -> box(5, 5, 0, 11, 11, 4);
      case EAST -> box(0, 5, 5, 4, 11, 11);
      case WEST -> box(12, 5, 5, 16, 11, 11);
      case UP -> box(5, 0, 5, 11, 4, 11);
      case DOWN -> box(5, 12, 5, 11, 16, 11);
    };
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
    super.onPlace(state, level, pos, old, moved);
    if (!old.is(this)) {
      level.scheduleTick(pos, this, 1);
    }
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!state.canSurvive(level, pos)) {
      level.destroyBlock(pos, true);
      return;
    }
    var body = CoreAssemblyRules.containing(level, pos);
    if (body == null || !CoreAssemblyRules.isCreature(body) || SubLevelHealth.status(body).dead()) {
      level.setBlock(pos, EyeBlink.sleeping(state), Block.UPDATE_CLIENTS);
      return;
    }
    if (org.Azkerash.create_bioships
        .combat
        .SubLevelWounds
        .state(body)
        .active(level.getGameTime())
        .stream()
        .anyMatch(w -> w.surface().cell().equals(pos))) {
      level.setBlock(pos, EyeBlink.sleeping(state), Block.UPDATE_CLIENTS);
      level.scheduleTick(pos, this, 20);
      return;
    }
    EyeBlink.advance(this, state, level, pos);
  }

  @Override
  public void afterMove(
      ServerLevel source, ServerLevel destination, BlockState state, BlockPos from, BlockPos to) {
    destination.setBlock(
        to, EyeBlink.sleeping(state).setValue(EyeBlink.WAKE, 1), Block.UPDATE_CLIENTS);
    destination.scheduleTick(to, this, 1);
  }
}
