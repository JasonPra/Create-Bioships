package org.Azkerash.create_bioships.client.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public abstract class VisionDebugKeyMixin {
  // Veil enables vanilla's chunk-debug handler before the usual debug handler in dev runs.
  @Inject(
      method = {"handleDebugKeys", "handleChunkDebugKeys"},
      at = @At("HEAD"),
      cancellable = true)
  private void core$vision(int key, CallbackInfoReturnable<Boolean> callback) {
    if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
      org.Azkerash.create_bioships.client.development.VisionDebugOverlay.toggle();
      callback.setReturnValue(true);
    }
  }
}
