package org.Azkerash.create_bioships.components.logic.eye;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Shared sensory weak-point rules; adding an eye type does not change combat damage routing. */
public final class EyeWoundBehavior {
  public static final int WOUND_TICKS = 1200;
  public static final double DAMAGE_MULTIPLIER = 1.5;

  private static final Class<?>[] INVALID_SUPPORT_TYPES = {EyeBlock.class};

  public static boolean hasSupport(
      BlockState eye,
      net.minecraft.world.level.BlockGetter level,
      net.minecraft.core.BlockPos pos) {
    var face = eye.getValue(BlockStateProperties.FACING);
    var behind = pos.relative(face.getOpposite());
    var support = level.getBlockState(behind);
    for (var type : INVALID_SUPPORT_TYPES) {
      if (type.isInstance(support.getBlock())) {
        return false;
      }
    }
    return support.isCollisionShapeFullBlock(level, behind)
        && support.isFaceSturdy(level, behind, face);
  }

  private EyeWoundBehavior() {}

  public static boolean isEye(BlockState state) {
    return state.getBlock() instanceof EyeBlock;
  }

  public static boolean isWeakPoint(BlockState state, Direction face) {
    return state.is(ComponentRegistry.CORE.get())
        || isEye(state) && state.getValue(BlockStateProperties.FACING) == face;
  }
}
