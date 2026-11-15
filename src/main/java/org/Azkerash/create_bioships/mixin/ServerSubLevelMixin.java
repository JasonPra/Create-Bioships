package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSubLevel.class)
public class ServerSubLevelMixin {
  @Inject(method = "setSplitFrom", at = @At("TAIL"), remap = false)
  private void inherit(ServerSubLevel original, Pose3d pose, CallbackInfo callback) {
    if (org.Azkerash.create_bioships.BioshipDecay.dead(original))
      org.Azkerash.create_bioships.BioshipDecay.inherit((ServerSubLevel) (Object) this, original);
    if (BioshipIntegration.bound(original)) BioshipIntegration.bind((ServerSubLevel) (Object) this);
  }
}
