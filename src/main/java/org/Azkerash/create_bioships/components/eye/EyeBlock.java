package org.Azkerash.create_bioships.components.eye;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.core.*;

/** Small outward-facing sensory component; land and aquatic forms share all behavior. */
public final class EyeBlock extends Block implements BlockSubLevelAssemblyListener {
  public static final MapCodec<EyeBlock> CODEC = simpleCodec(EyeBlock::new);

  public EyeBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        EyeBlink.sleeping(
            stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH)));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(BlockStateProperties.FACING, EyeBlink.BLINK, EyeBlink.WAKE, EyeBlink.STAGE);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
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
    if (!old.is(this)) level.scheduleTick(pos, this, 1);
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    var body = CoreAssemblyRules.containing(level, pos);
    if (body == null || !CoreAssemblyRules.isCreature(body) || SubLevelHealth.status(body).dead()) {
      level.setBlock(pos, EyeBlink.sleeping(state), 2);
      return;
    }
    EyeBlink.advance(this, state, level, pos);
  }

  @Override
  public void afterMove(
      ServerLevel source, ServerLevel destination, BlockState state, BlockPos from, BlockPos to) {
    destination.setBlock(to, EyeBlink.sleeping(state).setValue(EyeBlink.WAKE, 1), 2);
    destination.scheduleTick(to, this, 1);
  }
}
