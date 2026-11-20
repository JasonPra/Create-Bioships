package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.Entity;
import org.Azkerash.create_bioships.combat.ProjectileAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress only the native impact discard while transferring an arrow into attached state. */
@Mixin(Entity.class)
public abstract class EmbeddedProjectileRetentionMixin {
  @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
  private void core$retain(CallbackInfo callback) {
    if (ProjectileAttachment.retain((Entity) (Object) this)) {
      callback.cancel();
    }
  }
}
