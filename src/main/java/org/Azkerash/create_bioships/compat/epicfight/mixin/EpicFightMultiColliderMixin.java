package org.Azkerash.create_bioships.compat.epicfight.mixin;

import java.util.*;
import net.minecraft.world.entity.Entity;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.compat.epicfight.EpicFightBlockCollisions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Multiple samples of the same swing must not damage one grid through several adapter entities. */
@Pseudo
@Mixin(targets = "yesman.epicfight.api.collider.MultiCollider", remap = false)
public abstract class EpicFightMultiColliderMixin {
  @Inject(method = "getCollideEntities", at = @At("RETURN"), cancellable = true)
  private void core$oneHitPerGrid(Entity attacker, CallbackInfoReturnable<List<Entity>> callback) {
    callback.setReturnValue(EpicFightBlockCollisions.onePerGrid(callback.getReturnValue()));
  }

  @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
      method = "updateAndSelectCollideEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
              remap = true))
  private List<Entity> core$sampledTissue(
      net.minecraft.world.level.Level level,
      Entity attacker,
      net.minecraft.world.phys.AABB bounds,
      java.util.function.Predicate<? super Entity> predicate,
      com.llamalad7.mixinextras.injector.wrapoperation.Operation<List<Entity>> original,
      @com.llamalad7.mixinextras.sugar.Local(ordinal = 0) List<?> samples) {
    return EpicFightBlockCollisions.collectSamples(
        attacker, original.call(level, attacker, bounds, predicate), samples);
  }
}
