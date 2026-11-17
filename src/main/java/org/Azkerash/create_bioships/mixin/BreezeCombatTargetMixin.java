package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import org.Azkerash.create_bioships.Create_bioships;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Breeze's native player/golem allowlist otherwise rejects the sublevel attack adapter. */
@Mixin(Breeze.class)
public abstract class BreezeCombatTargetMixin {
  @Inject(method = "canAttackType", at = @At("HEAD"), cancellable = true)
  private void core$allowGrid(EntityType<?> type, CallbackInfoReturnable<Boolean> callback) {
    if (type == Create_bioships.COMBAT_TARGET.get()) callback.setReturnValue(true);
  }
}
