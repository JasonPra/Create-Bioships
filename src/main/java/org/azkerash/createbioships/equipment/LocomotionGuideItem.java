package org.azkerash.createbioships.equipment;

import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Read-only, resource-backed manual; opening it never edits the creature being inspected. */
public final class LocomotionGuideItem extends Item {
  public static final String RESOURCE = "guide/locomotion.json";

  public LocomotionGuideItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (level.isClientSide) {
      org.azkerash.createbioships.client.components.CerebralGuideScreen.open(
          RESOURCE, Component.translatable(getDescriptionId()));
    }
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, java.util.List<Component> lines, TooltipFlag flag) {
    lines.add(Component.translatable(getDescriptionId() + ".tooltip"));
  }
}
