package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.Azkerash.create_bioships.BioshipIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSubLevel.class)
public class ServerSubLevelMixin {
    @Inject(method = "setSplitFrom", at = @At("TAIL"), remap = false)
    private void inherit(ServerSubLevel original, Pose3d pose, CallbackInfo callback) {
        if (BioshipIntegration.bound(original)) BioshipIntegration.bind((ServerSubLevel)(Object)this);
    }
}
