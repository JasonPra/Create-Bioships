package org.Azkerash.create_bioships.components.logic.eye;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Shared sensory weak-point rules; adding an eye type does not change combat damage routing. */
public final class EyeWoundBehavior {
  public static final int WOUND_TICKS = 1200;
  public static final double DAMAGE_MULTIPLIER = 1.5;

  private EyeWoundBehavior() {}

  public static boolean isEye(BlockState state) {
    return state.getBlock() instanceof EyeBlock;
  }

  public static boolean isWeakPoint(BlockState state, Direction face) {
    return state.is(ComponentRegistry.CORE.get())
        || isEye(state) && state.getValue(BlockStateProperties.FACING) == face;
  }
}
