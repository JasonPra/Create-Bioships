package org.Azkerash.create_bioships.mixin;

import com.github.elenterius.biomancy.block.membrane.BiometricMembraneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiometricMembraneBlockEntity.class)
public abstract class BiometricMembraneAccessMixin {
  @Inject(
      method = "shouldIgnoreEntityCollisionAt",
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private void creature$permissions(
      BlockState state,
      BlockGetter level,
      BlockPos pos,
      Entity entity,
      CallbackInfoReturnable<Boolean> callback) {
    var allowed =
        org.Azkerash.create_bioships.compat.biomancy.MembraneAccess.permits(level, pos, entity);
    if (allowed != null) {
      callback.setReturnValue(allowed);
    }
  }
}
