package org.Azkerash.create_bioships.equipment;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Our restricted variant of Let It Walk's Positioner, not a wrench or remote controller. */
public final class LimbPositionerItem extends Item {
  public LimbPositionerItem(Properties properties) {
    super(properties);
  }

  @Override
  public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
      net.minecraft.world.level.Level level,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand) {
    if (player instanceof net.minecraft.server.level.ServerPlayer server) {
      var target = org.Azkerash.create_bioships.compat.letitwalk.SocketJointActions.pick(server);
      if (target == null) {
        return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
      }
      org.Azkerash.create_bioships.compat.letitwalk.SocketJointActions.show(server, target);
    }
    return net.minecraft.world.InteractionResultHolder.sidedSuccess(
        player.getItemInHand(hand), level.isClientSide);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var joint = org.Azkerash.create_bioships.compat.letitwalk.SocketJointActions.useOn(context);
    if (joint != InteractionResult.PASS) {
      return joint;
    }
    return org.Azkerash.create_bioships.compat.letitwalk.LimbPositionerActions.use(
        org.Azkerash.create_bioships.compat.letitwalk.CompactLimbController.socketContext(context));
  }
}
