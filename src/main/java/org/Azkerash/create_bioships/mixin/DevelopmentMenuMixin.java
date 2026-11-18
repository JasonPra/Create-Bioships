package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.Azkerash.create_bioships.development.testing.DevelopmentLoadout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class DevelopmentMenuMixin {
  @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
  private void core$lockedNavigator(
      int slot, int button, ClickType type, Player player, CallbackInfo callback) {
    if (DevelopmentLoadout.rejectsClick(
        (AbstractContainerMenu) (Object) this, slot, button, type, player)) {
      if (player instanceof net.minecraft.server.level.ServerPlayer server)
        DevelopmentLoadout.synchronize(server);
      callback.cancel();
    }
  }
}
