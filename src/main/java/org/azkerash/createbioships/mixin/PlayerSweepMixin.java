package org.azkerash.createbioships.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.azkerash.createbioships.combat.CombatAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve vanilla sweep damage and weapon hitboxes while selecting each intersected grid once. */
@Mixin(Player.class)
public abstract class PlayerSweepMixin {
  @WrapOperation(
      method = "attack",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
  private List<LivingEntity> core$sweepTissue(
      Level level,
      Class<LivingEntity> type,
      AABB bounds,
      Operation<List<LivingEntity>> original,
      @Local(argsOnly = true) Entity primary) {
    return CombatAdapter.sweepTargets(
        (Player) (Object) this, bounds, primary, original.call(level, type, bounds));
  }
}
