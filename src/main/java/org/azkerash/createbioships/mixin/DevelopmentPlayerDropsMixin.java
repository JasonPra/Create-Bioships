package org.azkerash.createbioships.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.azkerash.createbioships.development.testing.DevelopmentLoadout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class DevelopmentPlayerDropsMixin {
  @Inject(
      method =
          "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"),
      cancellable = true)
  private void core$keepNavigator(
      ItemStack stack, boolean around, boolean name, CallbackInfoReturnable<ItemEntity> callback) {
    if (DevelopmentLoadout.protectedItem((Player) (Object) this, stack)) {
      callback.setReturnValue(null);
    }
  }
}
