package org.Azkerash.create_bioships.components.logic.legs;

import dev.letitwalk.content.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Native Let It Walk hinge/swivel semantics with our armored shell and entity registration. */
public class ModularJointBlock extends LegJointBlock
    implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
  public ModularJointBlock(boolean swivel, Properties properties) {
    super(swivel, properties);
    registerDefaultState(
        defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false));
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.ENTITYBLOCK_ANIMATED;
  }

  @Override
  public BlockEntityType<? extends LegJointBlockEntity> getBlockEntityType() {
    return ComponentRegistry.MODULAR_JOINT_ENTITY.get();
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ModularJointEntity(pos, state);
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
    return org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock.placed(
        super.getStateForPlacement(c), c);
  }

  @Override
  protected net.minecraft.world.level.material.FluidState getFluidState(
      net.minecraft.world.level.block.state.BlockState s) {
    return org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock.fluid(s);
  }

  @Override
  protected net.minecraft.world.level.block.state.BlockState updateShape(
      net.minecraft.world.level.block.state.BlockState s,
      net.minecraft.core.Direction d,
      net.minecraft.world.level.block.state.BlockState other,
      net.minecraft.world.level.LevelAccessor l,
      net.minecraft.core.BlockPos p,
      net.minecraft.core.BlockPos q) {
    org.Azkerash.create_bioships.components.logic.WaterloggedComponentBlock.schedule(s, l, p);
    return super.updateShape(s, d, other, l, p, q);
  }
}
