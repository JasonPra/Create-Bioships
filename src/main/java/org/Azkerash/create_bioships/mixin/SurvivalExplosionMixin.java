package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.level.Explosion;
import org.Azkerash.create_bioships.BioshipSurvivalAdaptations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
abstract class SurvivalExplosionMixin {
  @Inject(method = "explode", at = @At("HEAD"))
  private void bioships$begin(CallbackInfo ci) {
    BioshipSurvivalAdaptations.begin((Explosion) (Object) this);
  }

  @Inject(method = "explode", at = @At("RETURN"))
  private void bioships$end(CallbackInfo ci) {
    BioshipSurvivalAdaptations.end();
  }
}
