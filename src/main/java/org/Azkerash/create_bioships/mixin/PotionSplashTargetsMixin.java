package org.Azkerash.create_bioships.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.Azkerash.create_bioships.combat.CombatAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownPotion.class)
public abstract class PotionSplashTargetsMixin {
  @WrapOperation(
      method = "applySplash",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
  private List<LivingEntity> core$onePotionRecipient(
      Level level, Class<LivingEntity> type, AABB bounds, Operation<List<LivingEntity>> original) {
    return CombatAdapter.potionTargets(level, bounds, original.call(level, type, bounds));
  }

  @WrapOperation(
      method = "applySplash",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
  private boolean core$regenerationCare(
      LivingEntity recipient,
      net.minecraft.world.effect.MobEffectInstance effect,
      net.minecraft.world.entity.Entity source,
      Operation<Boolean> original) {
    boolean alreadyRegenerating =
        recipient.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
    boolean accepted = original.call(recipient, effect, source);
    org.Azkerash.create_bioships.core.trust.RegenerationTrust.applied(
        (ThrownPotion) (Object) this, recipient, effect, accepted, alreadyRegenerating);
    return accepted;
  }
}
