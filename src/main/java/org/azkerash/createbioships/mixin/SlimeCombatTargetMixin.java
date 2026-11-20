package org.azkerash.createbioships.mixin;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Slime;
import org.azkerash.createbioships.combat.SubLevelCombatTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend vanilla player/golem contact damage using the same native contact/damage checks. */
@Mixin(Slime.class)
public abstract class SlimeCombatTargetMixin {
  @Shadow
  protected abstract boolean isDealsDamage();

  @Shadow
  protected abstract void dealDamage(LivingEntity target);

  @Inject(method = "push", at = @At("TAIL"))
  private void core$contact(Entity entity, CallbackInfo callback) {
    if (entity instanceof SubLevelCombatTarget target && isDealsDamage()) {
      dealDamage(target);
    }
  }
}
