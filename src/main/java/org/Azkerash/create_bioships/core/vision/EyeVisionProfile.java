package org.Azkerash.create_bioships.core.vision;

import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.eye.EyeBlock;

/** Sensory tuning only; scheduling, ownership and casting belong to the grid controller. */
public record EyeVisionProfile(boolean aquatic) {
  public static final EyeVisionProfile LAND = new EyeVisionProfile(false),
      AQUATIC = new EyeVisionProfile(true);

  public static EyeVisionProfile of(BlockState state) {
    if (state.is(ComponentRegistry.AQUATIC_EYE.get())) return AQUATIC;
    if (state.is(ComponentRegistry.CORE.get()) || state.getBlock() instanceof EyeBlock) return LAND;
    return null;
  }

  public double range(boolean underwater, int light) {
    double base = underwater == aquatic ? 24 : 12;
    double minimum = aquatic && underwater ? .6 : .25;
    return base * (minimum + (1 - minimum) * Math.clamp(light, 0, 15) / 15.0);
  }
}
