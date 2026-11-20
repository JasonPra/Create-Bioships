package org.azkerash.createbioships.compat.epicfight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.Entity;
import org.azkerash.createbioships.compat.epicfight.EpicFightBlockCollisions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.types.AttackAnimation", remap = false)
public abstract class EpicFightContactVisibilityMixin {
  @WrapOperation(
      method = "hurtCollidingEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lyesman/epicfight/api/utils/math/MathUtils;canBeSeen(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;D)Z"))
  private boolean core$tissueContact(
      Entity target, Entity watcher, double distance, Operation<Boolean> original) {
    return original.call(target, watcher, distance)
        || EpicFightBlockCollisions.visibleContact(target, watcher);
  }
}
