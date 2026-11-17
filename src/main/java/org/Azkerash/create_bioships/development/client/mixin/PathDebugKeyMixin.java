package org.Azkerash.create_bioships.development.client.mixin;

import net.minecraft.client.KeyboardHandler;
import org.Azkerash.create_bioships.development.client.PathDebugOverlay;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
abstract class PathDebugKeyMixin {
  @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
  private void bioships$pathDebug(int key, CallbackInfoReturnable<Boolean> callback) {
    if (key == GLFW.GLFW_KEY_P) {
      PathDebugOverlay.toggle();
      callback.setReturnValue(true);
    }
  }
}
