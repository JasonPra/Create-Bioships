package org.Azkerash.create_bioships.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.Azkerash.create_bioships.BioshipAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplosionDamageCalculator.class)
abstract class SurvivalBlastResistanceMixin {
  @Inject(method = "getBlockExplosionResistance", at = @At("RETURN"), cancellable = true)
  private void bioships$resistance(
      Explosion blast,
      BlockGetter reader,
      BlockPos pos,
      BlockState state,
      FluidState fluid,
      CallbackInfoReturnable<Optional<Float>> ci) {
    if (!(reader instanceof Level level) || level.isClientSide || ci.getReturnValue().isEmpty())
      return;
    var core = BioshipAccess.core(level, pos);
    if (core != null && !core.body().dead())
      ci.setReturnValue(Optional.of(core.survival().blockResistance(ci.getReturnValue().get())));
  }
}
