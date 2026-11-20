package org.azkerash.createbioships.mixin;

import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.compat.letitwalk.SocketJointRig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Our collidable sockets cannot use the upstream fallback that accepts a colliding foothold. */
@Mixin(value = WalkerBrain.class, remap = false)
public abstract class SocketFootholdMixin {
  @ModifyConstant(method = "findFoothold", constant = @Constant(doubleValue = 2.5))
  private static double bioships$rejectCollision(
      double penalty,
      Level level,
      ServerSubLevel sub,
      Pose3dc pose,
      BlockPos core,
      List<LegRuntime> legs,
      LegRuntime leg,
      Vec3 hip,
      Vec3 target,
      Vec3 shift) {
    return SocketJointRig.decorated(leg.built) ? Double.POSITIVE_INFINITY : penalty;
  }
}
