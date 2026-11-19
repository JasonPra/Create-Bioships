package org.Azkerash.create_bioships.components.logic.legs;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** Passive armored bone; native assembly groups connected bone blocks between joints. */
public final class ModularSegmentBlock extends RotatedPillarBlock
    implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
  public static final MapCodec<ModularSegmentBlock> CODEC = simpleCodec(ModularSegmentBlock::new);

  public ModularSegmentBlock(Properties p) {
    super(p);
    registerDefaultState(
        defaultBlockState()
            .setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                false));
  }

  @Override
  public MapCodec<? extends RotatedPillarBlock> codec() {
    return CODEC;
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
