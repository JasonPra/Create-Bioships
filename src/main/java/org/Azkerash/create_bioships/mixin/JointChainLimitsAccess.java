package org.Azkerash.create_bioships.mixin;

import dev.letitwalk.core.JointChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Native IK accepts uniform limits publicly; expose its existing per-axis arrays for our sockets.
 */
@Mixin(value = JointChain.class, remap = false)
public interface JointChainLimitsAccess {
  @Accessor("preferred")
  double[] bioships$preferred();

  @Accessor("seeds")
  double[][] bioships$seeds();

  @Accessor("lo")
  double[] bioships$minimum();

  @Accessor("hi")
  double[] bioships$maximum();
}
