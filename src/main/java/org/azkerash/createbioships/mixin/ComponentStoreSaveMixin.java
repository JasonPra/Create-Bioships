package org.azkerash.createbioships.mixin;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Flush cached component state before Sable captures a save/unload snapshot. */
@Mixin(dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.class)
public abstract class ComponentStoreSaveMixin {
  @Inject(method = "toData", at = @At("HEAD"), remap = false)
  private static void core$flush(
      ServerSubLevel body,
      java.util.List<java.util.UUID> dependencies,
      CallbackInfoReturnable<dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData>
          callback) {
    org.azkerash.createbioships.core.component.SubLevelComponentStore.flush(body);
  }
}
