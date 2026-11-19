package org.Azkerash.create_bioships.mixin;

import dev.letitwalk.walker.WalkerSnap;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use the native post-physics pose queue after converting compact collision-bone rest frames. */
@Mixin(value = WalkerSnap.class, remap = false)
public interface WalkerSnapInvoker {
  @Invoker("posed")
  static void bioships$posed(
      ServerSubLevel body,
      Vector3d position,
      Quaterniond turn,
      Vector3d velocity,
      Vector3d spin,
      double dt) {
    throw new AssertionError();
  }
}
