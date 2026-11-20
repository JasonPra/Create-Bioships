package org.azkerash.createbioships.components.logic.legs;

import dev.letitwalk.content.LegFootBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.azkerash.createbioships.components.ComponentRegistry;

/** Normal collision/friction foot, never a climbing or world-anchoring foot. */
public final class ModularFootBlock extends LegFootBlock
    implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
  public ModularFootBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false));
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return Shapes.or(
        net.minecraft.world.level.block.Block.box(1, 0, 1, 15, 4, 15),
        net.minecraft.world.level.block.Block.box(3, 4, 3, 13, 6, 13),
        net.minecraft.world.level.block.Block.box(5, 5, 5, 11, 16, 11));
  }

  @Override
  protected VoxelShape getCollisionShape(
      BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return getShape(s, l, p, c);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new FootEntity(p, s);
  }

  public static final class FootEntity extends LegFootBlockEntity {
    public FootEntity(BlockPos p, BlockState s) {
      super(p, s);
    }

    @Override
    public BlockEntityType<?> getType() {
      return ComponentRegistry.MODULAR_FOOT_ENTITY.get();
    }
  }

  @Override
  protected void createBlockStateDefinition(
      net.minecraft.world.level.block.state.StateDefinition.Builder<
              net.minecraft.world.level.block.Block,
              net.minecraft.world.level.block.state.BlockState>
          b) {
    super.createBlockStateDefinition(b);
    b.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
  }

  @Override
  public net.minecraft.world.level.block.state.BlockState getStateForPlacement(
      net.minecraft.world.item.context.BlockPlaceContext c) {
    return org.azkerash.createbioships.components.logic.WaterloggedComponentBlock.placed(
        super.getStateForPlacement(c), c);
  }

  @Override
  protected net.minecraft.world.level.material.FluidState getFluidState(
      net.minecraft.world.level.block.state.BlockState s) {
    return org.azkerash.createbioships.components.logic.WaterloggedComponentBlock.fluid(s);
  }

  @Override
  protected net.minecraft.world.level.block.state.BlockState updateShape(
      net.minecraft.world.level.block.state.BlockState s,
      net.minecraft.core.Direction d,
      net.minecraft.world.level.block.state.BlockState other,
      net.minecraft.world.level.LevelAccessor l,
      net.minecraft.core.BlockPos p,
      net.minecraft.core.BlockPos q) {
    org.azkerash.createbioships.components.logic.WaterloggedComponentBlock.schedule(s, l, p);
    return super.updateShape(s, d, other, l, p, q);
  }
}
