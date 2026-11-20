package org.azkerash.createbioships.components.logic.brain;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.azkerash.createbioships.core.brain.behavior.HuntingBehavior;

/** A fixed hunting program whose editable input is restricted to prey species. */
public final class HuntingBehaviorCardItem extends FactoryBehaviorCardItem {
  public HuntingBehaviorCardItem() {
    super(HuntingBehavior.ID);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    var card = player.getItemInHand(hand);
    if (player instanceof ServerPlayer server) {
      int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
      HuntingCardMenu.open(server, player.getInventory(), slot, () -> true);
    }
    return InteractionResultHolder.sidedSuccess(card, level.isClientSide);
  }
}
