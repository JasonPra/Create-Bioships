package org.Azkerash.create_bioships.mixin;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.equipment.BioshipWrenchItem;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes only the delivery of drops during the ossified wrench's supported-grid action. */
@Mixin(Inventory.class)
public abstract class WrenchDropDeliveryMixin {
  @Shadow @Final public Player player;

  @Inject(
      method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void core$dropInsteadOfInsert(ItemStack stack, CallbackInfo callback) {
    if (BioshipWrenchItem.dropRemovalStack(player, stack)) callback.cancel();
  }
}
