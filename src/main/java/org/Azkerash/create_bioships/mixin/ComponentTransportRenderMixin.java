package org.Azkerash.create_bioships.mixin;

import org.Azkerash.create_bioships.amoeba.ComponentTransport;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.Azkerash.create_bioships.amoeba.client.ComponentTransportRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientSubLevel.class, remap = false)
public class ComponentTransportRenderMixin {
  @Inject(
      method = "renderPose(F)Ldev/ryanhcode/sable/companion/math/Pose3dc;",
      at = @At("RETURN"),
      cancellable = true)
  private void mergingPose(float partial, CallbackInfoReturnable<Pose3dc> cir) {
    cir.setReturnValue(
        ComponentTransportRendering.pose(
            (ClientSubLevel) (Object) this, cir.getReturnValue(), partial));
  }
}
