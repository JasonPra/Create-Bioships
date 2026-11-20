package org.Azkerash.create_bioships.furniture;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock;

/** Joining tables keep their supports at the outside corners of the assembled surface. */
public final class OssuaryTableBlock extends WaterloggedComponentBlock {
  public static final MapCodec<OssuaryTableBlock> CODEC = simpleCodec(OssuaryTableBlock::new);
  public static final Map<Direction, BooleanProperty> CONNECTIONS =
      Map.of(
          Direction.NORTH,
          BlockStateProperties.NORTH,
          Direction.SOUTH,
          BlockStateProperties.SOUTH,
          Direction.EAST,
          BlockStateProperties.EAST,
          Direction.WEST,
          BlockStateProperties.WEST);

  public OssuaryTableBlock(Properties properties) {
    super(properties);
    var state = defaultBlockState();
    for (var property : CONNECTIONS.values()) state = state.setValue(property, false);
    registerDefaultState(state);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(
        BlockStateProperties.NORTH,
        BlockStateProperties.SOUTH,
        BlockStateProperties.EAST,
        BlockStateProperties.WEST);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    var state = super.getStateForPlacement(context);
    for (var entry : CONNECTIONS.entrySet())
      state =
          state.setValue(
              entry.getValue(),
              context
                  .getLevel()
                  .getBlockState(context.getClickedPos().relative(entry.getKey()))
                  .is(this));
    return state;
  }

  @Override
  protected BlockState updateShape(
      BlockState state,
      Direction side,
      BlockState neighbor,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    state = super.updateShape(state, side, neighbor, level, pos, neighborPos);
    var property = CONNECTIONS.get(side);
    return property == null ? state : state.setValue(property, neighbor.is(this));
  }

  @Override
  public BlockState rotate(BlockState state, Rotation rotation) {
    var result = state;
    for (var side : CONNECTIONS.keySet())
      result =
          result.setValue(
              CONNECTIONS.get(rotation.rotate(side)), state.getValue(CONNECTIONS.get(side)));
    return result;
  }

  @Override
  public BlockState mirror(BlockState state, Mirror mirror) {
    var result = state;
    for (var side : CONNECTIONS.keySet())
      result =
          result.setValue(
              CONNECTIONS.get(mirror.mirror(side)), state.getValue(CONNECTIONS.get(side)));
    return result;
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    var shape = box(0, 12, 0, 16, 16, 16);
    for (var northSouth : new Direction[] {Direction.NORTH, Direction.SOUTH})
      for (var eastWest : new Direction[] {Direction.EAST, Direction.WEST})
        if (!state.getValue(CONNECTIONS.get(northSouth))
            && !state.getValue(CONNECTIONS.get(eastWest))) {
          int x = eastWest == Direction.WEST ? 1 : 12;
          int z = northSouth == Direction.NORTH ? 1 : 12;
          shape = Shapes.or(shape, box(x, 0, z, x + 3, 12, z + 3));
        }
    return shape;
  }
}
