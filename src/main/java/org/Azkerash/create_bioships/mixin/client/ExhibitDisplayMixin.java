package org.Azkerash.create_bioships.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import org.Azkerash.create_bioships.client.development.ExhibitDisplayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.class)
public abstract class ExhibitDisplayMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/world/entity/Display;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at = @At("HEAD"),
      cancellable = true)
  private void bioships$deferPanel(
      Display display,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      CallbackInfo ci) {
    if (ExhibitDisplayRenderer.defer(
        (DisplayRenderer<?, ?>) (Object) this, display, yaw, partial, pose, buffers, light)) {
      ci.cancel();
    }
  }
}
