package org.azkerash.createbioships.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** A normal player tool; its two independent diagnostic modes are selected in a radial menu. */
public final class SomaticResonatorItem extends Item {
  public SomaticResonatorItem() {
    super(new Properties().stacksTo(1));
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server) {
      PacketDistributor.sendToPlayer(server, new SomaticMenuPayload());
    }
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
