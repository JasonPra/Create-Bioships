package org.azkerash.createbioships.core.vision;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Optical metres spent per physical metre inside a voxel. Geometry still decides solid hits. */
public final class VisionMaterials {
  public static final double STANDARD_COST = 1;
  public static final double LAND_WATER_COST = 3;
  public static final double COLORED_GLASS_COST = 2;

  private VisionMaterials() {}

  public static boolean glass(BlockState state) {
    return state.is(Blocks.GLASS)
        || state.is(Blocks.GLASS_PANE)
        || state.getBlock() instanceof StainedGlassBlock
        || state.getBlock() instanceof StainedGlassPaneBlock;
  }

  public static double cost(BlockState state, boolean aquatic) {
    double cost =
        !aquatic && state.getFluidState().is(FluidTags.WATER) ? LAND_WATER_COST : STANDARD_COST;
    if (state.getBlock() instanceof StainedGlassBlock
        || state.getBlock() instanceof StainedGlassPaneBlock) {
      cost = Math.max(cost, COLORED_GLASS_COST);
    }
    return cost;
  }
}
