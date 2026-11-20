package org.azkerash.createbioships.compat.epicfight.mixin;

import java.util.List;
import net.minecraft.world.entity.Entity;
import org.azkerash.createbioships.compat.epicfight.EpicFightBlockCollisions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "yesman.epicfight.api.collider.Collider", remap = false)
public abstract class EpicFightColliderMixin {
  @Inject(method = "getCollideEntities", at = @At("RETURN"), cancellable = true)
  private void core$tissueHits(Entity attacker, CallbackInfoReturnable<List<Entity>> callback) {
    callback.setReturnValue(
        EpicFightBlockCollisions.collect(this, attacker, callback.getReturnValue()));
  }
}
