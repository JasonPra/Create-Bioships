package org.Azkerash.create_bioships.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.Azkerash.create_bioships.LivingCoreBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary gaps during approved surgery must not become independently drifting physics bodies. */
@Mixin(value = SubLevelHeatMapManager.class, remap = false)
public class ReconfigurationHeatMapMixin {
  @Shadow @Final private ServerSubLevel subLevel;

  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  private void pauseSplitting(CallbackInfo ci) {
    for (var actor : subLevel.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core
          && (core.blueprint().locked() || core.rescue().active())
          && !core.body().dead()) {
        ci.cancel();
        return;
      }
  }
}
