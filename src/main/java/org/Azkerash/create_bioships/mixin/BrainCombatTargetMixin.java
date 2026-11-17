package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Goal-based acquisition must also reach brain-based native attack behaviors. */
@Mixin(Mob.class)
public abstract class BrainCombatTargetMixin {
  @Inject(method = "setTarget", at = @At("TAIL"))
  private void core$brainTarget(LivingEntity target, CallbackInfo callback) {
    org.Azkerash.create_bioships.combat.HostileCombatIntegration.updateBrainTarget(
        (Mob) (Object) this, target);
  }
}
