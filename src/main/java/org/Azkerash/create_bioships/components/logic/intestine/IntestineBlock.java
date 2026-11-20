package org.Azkerash.create_bioships.components.logic.intestine;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

public final class IntestineBlock
    extends org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock
    implements com.simibubi.create.content.equipment.wrench.IWrenchable {
  public static final Map<Direction, BooleanProperty> CONNECTIONS =
      Map.of(
          Direction.DOWN,
          BlockStateProperties.DOWN,
          Direction.UP,
          BlockStateProperties.UP,
          Direction.NORTH,
          BlockStateProperties.NORTH,
          Direction.SOUTH,
          BlockStateProperties.SOUTH,
          Direction.WEST,
          BlockStateProperties.WEST,
          Direction.EAST,
          BlockStateProperties.EAST);

  public IntestineBlock(Properties properties) {
    super(properties);
    var state =
        stateDefinition
            .any()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false);
    for (var property : CONNECTIONS.values()) {
      state = state.setValue(property, false);
    }
    registerDefaultState(state);
  }

  public static boolean compatible(BlockState state) {
    return state.getBlock()
            instanceof com.github.elenterius.biomancy.block.storagesac.StorageSacBlock
        || state.is(ComponentRegistry.INTESTINE.get())
        || state.getBlock()
            instanceof org.Azkerash.create_bioships.components.logic.mouth.MouthBlock
        || state.is(ComponentRegistry.STOMACH.get());
  }

  public static boolean compatible(BlockState state, Direction direction) {
    return compatible(state)
        && (!(state.getBlock()
                instanceof com.github.elenterius.biomancy.block.storagesac.StorageSacBlock)
            || direction.getAxis() == Direction.Axis.Y);
  }

  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    for (var property : CONNECTIONS.values()) {
      builder.add(property);
    }
  }

  public BlockState getStateForPlacement(BlockPlaceContext context) {
    var state = placed(defaultBlockState(), context);
    for (var e : CONNECTIONS.entrySet()) {
      state =
          state.setValue(
              e.getValue(),
              compatible(
                  context.getLevel().getBlockState(context.getClickedPos().relative(e.getKey())),
                  e.getKey()));
    }
    return state;
  }

  protected BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState other,
      LevelAccessor level,
      BlockPos pos,
      BlockPos otherPos) {
    return super.updateShape(
        state.setValue(CONNECTIONS.get(direction), compatible(other, direction)),
        direction,
        other,
        level,
        pos,
        otherPos);
  }

  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
    super.onPlace(state, level, pos, old, moved);
    if (!level.isClientSide) {
      var next = state;
      for (var e : CONNECTIONS.entrySet()) {
        next =
            next.setValue(
                e.getValue(),
                compatible(level.getBlockState(pos.relative(e.getKey())), e.getKey()));
      }
      if (!next.equals(state)) {
        level.setBlock(pos, next, 3);
      }
    }
  }

  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
    super.onRemove(state, level, pos, next, moved);
  }

  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    var shape = box(5, 5, 5, 11, 11, 11);
    for (var e : CONNECTIONS.entrySet()) {
      if (state.getValue(e.getValue())) {
        var d = e.getKey();
        shape =
            Shapes.or(
                shape,
                box(
                    d == Direction.WEST ? 0 : 6,
                    d == Direction.DOWN ? 0 : 6,
                    d == Direction.NORTH ? 0 : 6,
                    d == Direction.EAST ? 16 : 10,
                    d == Direction.UP ? 16 : 10,
                    d == Direction.SOUTH ? 16 : 10));
      }
    }
    return shape;
  }
}
