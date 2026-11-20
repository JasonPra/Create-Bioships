package org.azkerash.createbioships.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import org.azkerash.createbioships.CreateBioships;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Breeze's native player/golem allowlist otherwise rejects the sublevel attack adapter. */
@Mixin(Breeze.class)
public abstract class BreezeCombatTargetMixin {
  @Inject(method = "canAttackType", at = @At("HEAD"), cancellable = true)
  private void core$allowGrid(EntityType<?> type, CallbackInfoReturnable<Boolean> callback) {
    if (type == CreateBioships.COMBAT_TARGET.get()) {
      callback.setReturnValue(true);
    }
  }
}
