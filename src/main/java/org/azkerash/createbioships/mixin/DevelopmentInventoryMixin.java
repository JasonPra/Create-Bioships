package org.azkerash.createbioships.mixin;

import java.util.function.Predicate;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import org.azkerash.createbioships.development.testing.DevelopmentLoadout;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class DevelopmentInventoryMixin {
  @Shadow @Final public Player player;

  @ModifyVariable(method = "clearOrCountMatchingItems", at = @At("HEAD"), argsOnly = true)
  private Predicate<ItemStack> core$clearKeepsNavigator(Predicate<ItemStack> predicate) {
    return DevelopmentLoadout.active(player)
        ? stack -> !DevelopmentLoadout.managed(stack) && predicate.test(stack)
        : predicate;
  }

  @Inject(
      method = {"clearContent", "dropAll"},
      at = @At("TAIL"))
  private void core$restoreNavigator(CallbackInfo callback) {
    if (player instanceof net.minecraft.server.level.ServerPlayer server) {
      DevelopmentLoadout.ensureNavigator(server);
    }
  }
}
