package org.azkerash.createbioships.mixin;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.azkerash.createbioships.core.CreatureDecay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sable exposes split ancestry but has no public split event. */
@Mixin(ServerSubLevel.class)
public abstract class CreatureDecaySplitMixin {
  @Inject(method = "setSplitFrom", at = @At("TAIL"), remap = false)
  private void core$inheritRemains(ServerSubLevel parent, Pose3d pose, CallbackInfo callback) {
    CreatureDecay.inherit(parent, (ServerSubLevel) (Object) this);
  }
}
