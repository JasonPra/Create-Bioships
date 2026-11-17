package org.Azkerash.create_bioships.compat.epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.Entity;
import org.Azkerash.create_bioships.combat.SubLevelCombatTarget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", remap = false)
public abstract class EpicFightTargetMixin {
  @WrapOperation(
      method = "isTargetInvulnerable",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/Entity;isPickable()Z",
              remap = true))
  private boolean core$allowTissue(Entity target, Operation<Boolean> original) {
    return target instanceof SubLevelCombatTarget || original.call(target);
  }
}
