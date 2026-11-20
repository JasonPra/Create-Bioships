package org.Azkerash.create_bioships.mixin;

import dev.letitwalk.walker.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WalkerBrain.class, remap = false)
public abstract class CreatureFootstepsMixin {
  @Inject(method = "footfall", at = @At("HEAD"), cancellable = true)
  private static void bioships$surfaceStep(
      Level level, TerrainProbe.Ground ground, float weight, LegRuntime leg, CallbackInfo ci) {
    if (org.Azkerash.create_bioships.compat.letitwalk.CreatureFootsteps.play(
        level, ground, weight, leg)) {
      ci.cancel();
    }
  }
}
