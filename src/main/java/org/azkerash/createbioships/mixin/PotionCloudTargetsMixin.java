package org.azkerash.createbioships.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.List;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.azkerash.createbioships.combat.CombatAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
public abstract class PotionCloudTargetsMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
  private List<LivingEntity> core$oneCloudRecipient(
      Level level, Class<LivingEntity> type, AABB bounds, Operation<List<LivingEntity>> original) {
    return CombatAdapter.potionTargets(level, bounds, original.call(level, type, bounds));
  }
}
