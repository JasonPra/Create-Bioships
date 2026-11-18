package org.Azkerash.create_bioships.compat.epicfight.mixin;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.*;
import org.Azkerash.create_bioships.combat.SubLevelCombatTarget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Epic Fight normally rejects vanilla phases; allow native charging only against creature grids.
 */
@Pseudo
@Mixin(
    targets = "yesman.epicfight.world.capabilities.entitypatch.boss.enderdragon.PhaseManagerPatch",
    remap = false)
public abstract class EpicFightDragonPhaseMixin extends EnderDragonPhaseManager {
  protected EpicFightDragonPhaseMixin(EnderDragon dragon) {
    super(dragon);
  }

  @Inject(method = "setPhase", at = @At("HEAD"), cancellable = true)
  private void core$creatureCharge(EnderDragonPhase<?> phase, CallbackInfo callback) {
    if (phase == EnderDragonPhase.CHARGING_PLAYER
        && ((DragonPhaseManagerAccess) (Object) this).core$dragon().getTarget()
            instanceof SubLevelCombatTarget) {
      super.setPhase(phase);
      callback.cancel();
    }
  }
}
