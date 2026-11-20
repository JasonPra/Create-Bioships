package org.azkerash.createbioships.mixin;

import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.compat.letitwalk.SocketJointRig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Retain native collision/foothold tests while including every section of our shorter socket rigs.
 */
@Mixin(value = LegCollision.class, remap = false)
public abstract class SocketLegCollisionMixin {
  @Inject(method = "joints", at = @At("RETURN"), cancellable = true)
  private static void bioships$completeLeg(
      LegRuntime leg,
      Pose3dc pose,
      BlockPos core,
      Vec3 foot,
      CallbackInfoReturnable<List<Vec3>> callback) {
    if (SocketJointRig.decorated(leg.built)) {
      callback.setReturnValue(
          SocketJointRig.collisionSamples(leg.built, callback.getReturnValue()));
    }
  }

  @Inject(method = "hitsBlocks", at = @At("HEAD"), cancellable = true)
  private static void bioships$fullSampling(
      net.minecraft.world.level.Level level,
      List<Vec3> points,
      CallbackInfoReturnable<Boolean> callback) {
    if (points instanceof SocketJointRig.CollisionPoints marked) {
      callback.setReturnValue(
          LegCollision.hitsBlocks(level, SocketJointRig.completeSamples(marked)));
    }
  }
}
