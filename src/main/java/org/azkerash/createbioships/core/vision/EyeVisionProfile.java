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
    double visible = Double.isFinite(playerDistance) ? Math.clamp(playerDistance, 128, 512) : 128;
    double base = underwater == aquatic ? visible : visible / 2;
    double minimum = aquatic && underwater ? .6 : .25;
    return base * (minimum + (1 - minimum) * Math.clamp(light, 0, 15) / 15.0);
  }
}
