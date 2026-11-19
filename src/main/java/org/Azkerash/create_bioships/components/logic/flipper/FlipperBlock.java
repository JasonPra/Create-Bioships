package org.Azkerash.create_bioships.components.logic.flipper;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock;
import org.Azkerash.create_bioships.core.CoreAssemblyRules;
import org.Azkerash.create_bioships.core.flippers.SubLevelFlippers;

/** A side-mounted paddle; simulation belongs to its creature, not a block ticker. */
public final class FlipperBlock extends WaterloggedComponentBlock
    implements EntityBlock, BlockSubLevelAssemblyListener {
  public static final MapCodec<FlipperBlock> CODEC = simpleCodec(FlipperBlock::new);

  public FlipperBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
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
    var face = c.getClickedFace();
    if (!face.getAxis().isHorizontal()) return null;
    var state =
        placed(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face), c);
    return state.canSurvive(c.getLevel(), c.getClickedPos()) ? state : null;
  }

  @Override
  protected boolean canSurvive(BlockState s, LevelReader level, BlockPos p) {
    var face = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
    var behind = p.relative(face.getOpposite());
    return level.getBlockState(behind).isFaceSturdy(level, behind, face);
  }

  @Override
  protected BlockState updateShape(
      BlockState s, Direction d, BlockState other, LevelAccessor l, BlockPos p, BlockPos q) {
    if (!s.canSurvive(l, p)) l.scheduleTick(p, this, 1);
    return super.updateShape(s, d, other, l, p, q);
  }

  @Override
  protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
    if (!s.canSurvive(l, p)) l.destroyBlock(p, true);
  }

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(
        BlockStateProperties.HORIZONTAL_FACING,
        rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new FlipperBlockEntity(p, s);
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    // Conservative swept paddle envelope; the fin cannot visually swing through a solid wall.
    return box(0, 1, 0, 16, 15, 16);
  }

  @Override
  public void afterMove(
      ServerLevel source, ServerLevel destination, BlockState state, BlockPos from, BlockPos to) {
    var body = CoreAssemblyRules.containing(destination, to);
    if (body != null) SubLevelFlippers.request(body);
  }
}
