package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.Azkerash.create_bioships.core.FeedingTrust;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snapshot only player GUI actions, never autonomous inventory mutations. */
@Mixin(AbstractContainerMenu.class)
public abstract class FeedingMenuMixin {
  @Unique private FeedingTrust.Snapshot bioships$before;

  @Inject(method = "clicked", at = @At("HEAD"))
  private void bioships$beforeDeposit(
      int slot, int button, ClickType type, Player player, CallbackInfo ci) {
    if (!player.level().isClientSide) {
      bioships$before = FeedingTrust.snapshot((AbstractContainerMenu) (Object) this);
    }
  }

  @Inject(method = "clicked", at = @At("RETURN"))
  private void bioships$afterDeposit(
      int slot, int button, ClickType type, Player player, CallbackInfo ci) {
    if (!player.level().isClientSide && bioships$before != null) {
      FeedingTrust.deposited(
          bioships$before, FeedingTrust.snapshot((AbstractContainerMenu) (Object) this));
      bioships$before = null;
    }
  }
}
