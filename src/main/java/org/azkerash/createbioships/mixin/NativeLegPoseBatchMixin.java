package org.azkerash.createbioships.mixin;

import dev.letitwalk.walker.BuiltLegDriver;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.azkerash.createbioships.compat.letitwalk.NativeLegPoseBatch;
import org.joml.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Defer intermediate native writes only inside the compact adapter's target calculation. */
@Mixin(value = BuiltLegDriver.class, remap = false)
public abstract class NativeLegPoseBatchMixin {
  @Redirect(
      method = "substep",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;teleport(Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V"))
  private static void bioships$teleport(
      RigidBodyHandle handle, Vector3dc position, Quaterniondc turn) {
    if (!NativeLegPoseBatch.defersBodyWrites()) {
      handle.teleport(position, turn);
    }
  }

  @Redirect(
      method = "substep",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;addLinearAndAngularVelocity(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"))
  private static void bioships$velocity(
      RigidBodyHandle handle, Vector3dc velocity, Vector3dc spin) {
    if (!NativeLegPoseBatch.defersBodyWrites()) {
      handle.addLinearAndAngularVelocity(velocity, spin);
    }
  }

  @Redirect(
      method = "substep",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/letitwalk/walker/WalkerSnap;posed(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3d;Lorg/joml/Quaterniond;Lorg/joml/Vector3d;Lorg/joml/Vector3d;D)V"))
  private static void bioships$queuePose(
      ServerSubLevel body,
      Vector3d position,
      Quaterniond turn,
      Vector3d velocity,
      Vector3d spin,
      double dt) {
    if (!NativeLegPoseBatch.defersBodyWrites()) {
      WalkerSnapInvoker.bioships$posed(body, position, turn, velocity, spin, dt);
    }
  }
}
