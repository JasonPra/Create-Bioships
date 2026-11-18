package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import org.Azkerash.create_bioships.core.CreatureDecay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Unloading retains summaries; permanent deletion removes that fragment from its family. */
@Mixin(SubLevelHoldingChunkMap.class)
public abstract class CreatureDecayRemovalMixin {
  @Inject(method = "queueDeletion", at = @At("HEAD"), remap = false)
  private void core$forgetDeletedRemains(ServerSubLevel body, CallbackInfo callback) {
    org.Azkerash.create_bioships.core.SubLevelCreatureParts.removed(body);
    CreatureDecay.removed(body);
  }
}
