package org.azkerash.createbioships.equipment;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** The codex is read-only documentation and does not need access to any creature's private data. */
public final class CerebralGuideItem extends Item {
  public CerebralGuideItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    // Keep the screen behind the client-side branch so dedicated servers never load GUI classes.
    if (level.isClientSide) {
      org.azkerash.createbioships.client.components.CerebralGuideScreen.open();
    }
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
