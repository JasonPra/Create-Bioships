package org.Azkerash.create_bioships;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/**
 * Mineralized tissue has a shaped casing; its open corners must not hide neighboring hull faces.
 */
public final class BuoyancyOrganBlock extends Block {
  public static final MapCodec<BuoyancyOrganBlock> CODEC = simpleCodec(BuoyancyOrganBlock::new);
  private static final VoxelShape SHAPE =
      Shapes.or(
          Block.box(3, 0, 3, 13, 3, 13),
          Block.box(1, 3, 1, 15, 12, 15),
          Block.box(3, 12, 3, 13, 16, 13));

  public BuoyancyOrganBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }
}
