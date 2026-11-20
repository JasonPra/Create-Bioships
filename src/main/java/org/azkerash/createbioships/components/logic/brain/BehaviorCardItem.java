package org.azkerash.createbioships.components.logic.brain;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;

/** Portable blank card. Source editing is authoritative on the server; drafts do not execute. */
public final class BehaviorCardItem extends Item {
  private static CustomData blank() {
    var tag = new CompoundTag();
    var graph = new CompoundTag();
    graph.putInt("Version", 1);
    graph.put("Nodes", new ListTag());
    graph.put("Connections", new ListTag());
    graph.put("Variables", new ListTag());
    tag.put("BehaviorGraph", graph);
    return CustomData.of(tag);
  }

  public BehaviorCardItem() {
    super(new Properties().stacksTo(1).component(DataComponents.CUSTOM_DATA, blank()));
  }

  @Override
  public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
      net.minecraft.world.level.Level level,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand) {
    if (player instanceof net.minecraft.server.level.ServerPlayer server) {
      CardWorkspaces.openHeld(server, hand);
    }
    return net.minecraft.world.InteractionResultHolder.sidedSuccess(
        player.getItemInHand(hand), level.isClientSide);
  }
}
