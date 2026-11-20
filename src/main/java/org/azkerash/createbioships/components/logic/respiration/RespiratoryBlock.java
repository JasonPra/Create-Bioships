package org.azkerash.createbioships.components.logic.respiration;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;
import org.azkerash.createbioships.components.logic.WaterloggedComponentBlock;

/** An organ shell only; oxygen and environmental checks run on the creature controller. */
public final class RespiratoryBlock extends WaterloggedComponentBlock
    implements com.simibubi.create.content.equipment.wrench.IWrenchable {
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty BREATH =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create("breath", 0, 3);
  public static final net.minecraft.world.level.block.state.properties.BooleanProperty GASPING =
      net.minecraft.world.level.block.state.properties.BooleanProperty.create("gasping");
  private final boolean gills;
  private static final VoxelShape LUNG =
      Shapes.or(
          Block.box(2, 2, 2, 14, 14, 14), Block.box(0, 5, 5, 16, 11, 11),
          Block.box(5, 0, 5, 11, 16, 11), Block.box(5, 5, 0, 11, 11, 16));

  public RespiratoryBlock(Properties properties, boolean gills) {
    super(properties);
    this.gills = gills;
    registerDefaultState(
        defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.NORTH)
            .setValue(BREATH, 0)
            .setValue(GASPING, false));
  }

  public boolean gills() {
    return gills;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    super.createBlockStateDefinition(b);
    b.add(BlockStateProperties.FACING, BREATH, GASPING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return placed(defaultBlockState().setValue(BlockStateProperties.FACING, c.getClickedFace()), c);
  }

  @Override
  protected BlockState rotate(BlockState s, Rotation r) {
    return s.setValue(
        BlockStateProperties.FACING, r.rotate(s.getValue(BlockStateProperties.FACING)));
  }

  @Override
  protected BlockState mirror(BlockState s, Mirror m) {
    return s.rotate(m.getRotation(s.getValue(BlockStateProperties.FACING)));
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return gills ? Shapes.block() : LUNG;
  }
}
