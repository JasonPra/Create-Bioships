package org.azkerash.createbioships.core.vision;

import net.minecraft.world.level.block.state.BlockState;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.eye.EyeBlock;

/** Sensory tuning only; scheduling, ownership and casting belong to the grid controller. */
public record EyeVisionProfile(boolean aquatic) {
  public static final EyeVisionProfile LAND = new EyeVisionProfile(false),
      AQUATIC = new EyeVisionProfile(true);

  public static EyeVisionProfile of(BlockState state) {
    if (state.is(ComponentRegistry.AQUATIC_EYE.get())) {
      return AQUATIC;
    }
    if (state.is(ComponentRegistry.CORE.get()) || state.getBlock() instanceof EyeBlock) {
      return LAND;
    }
    return null;
  }

  public double range(boolean underwater, int light) {
    return range(underwater, light, 128);
  }

  public double range(boolean underwater, int light, double playerDistance) {
    return budget(underwater, light, playerDistance)
        / (underwater && !aquatic
            ? VisionMaterials.LAND_WATER_COST
            : VisionMaterials.STANDARD_COST);
  }

  /** Maximum optical budget; mixed media spend it along the ray, not at the eye's origin. */
  public double budget(boolean underwater, int light, double playerDistance) {
    double visible = Double.isFinite(playerDistance) ? Math.clamp(playerDistance, 128, 512) : 128;
    double minimum = aquatic && underwater ? .6 : .25;
    return visible * (minimum + (1 - minimum) * Math.clamp(light, 0, 15) / 15.0);
  }
}
