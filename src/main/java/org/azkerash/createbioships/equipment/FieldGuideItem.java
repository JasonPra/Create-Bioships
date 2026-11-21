package org.azkerash.createbioships.equipment;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Shared read-only manual opening; the item owns no creature or per-player program state. */
public class FieldGuideItem extends Item {
  private final String resource;

  public FieldGuideItem(Properties properties, String resource) {
    super(properties);
    this.resource = resource;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (level.isClientSide) {
      org.azkerash.createbioships.client.components.CerebralGuideScreen.open(
          resource, Component.translatable(getDescriptionId()));
    }
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    lines.add(Component.translatable(getDescriptionId() + ".tooltip"));
  }
}
