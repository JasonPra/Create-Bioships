package org.azkerash.createbioships.components.logic.brain;

import java.util.function.BooleanSupplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.brain.hunting.HuntingCardData;

/** Server editing lease bound to the exact card and its permission-checked inventory slot. */
public final class HuntingCardMenu extends AbstractContainerMenu {
  private final Container inventory;
  private final int slot;
  private final ItemStack card;
  private final BooleanSupplier access;
  private final String initial;
  private static final int SAVE_INTERVAL_TICKS = 10;
  private long nextSave;

  private HuntingCardMenu(
      int id, Container inventory, int slot, BooleanSupplier access, String initial) {
    super(ComponentRegistry.HUNTING_CARD_MENU.get(), id);
    this.inventory = inventory;
    this.slot = slot;
    this.access = access;
    card = inventory == null ? ItemStack.EMPTY : inventory.getItem(slot);
    this.initial = initial;
  }

  public static HuntingCardMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new HuntingCardMenu(id, null, 0, () -> false, buffer.readUtf(HuntingCardData.MAX_TEXT));
  }

  public static boolean open(
      ServerPlayer player, Container inventory, int slot, BooleanSupplier access) {
    if (slot < 0
        || slot >= inventory.getContainerSize()
        || !access.getAsBoolean()
        || !inventory.getItem(slot).is(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get())
        || inventory.getItem(slot).getCount() != 1) {
      return false;
    }
    String text =
        String.join(
            ", ",
            HuntingCardData.read(inventory.getItem(slot)).stream().map(Object::toString).toList());
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new HuntingCardMenu(id, inventory, slot, access, text),
            Component.translatable("item.create_bioships.hunting_behavior_card")),
        buffer -> buffer.writeUtf(text, HuntingCardData.MAX_TEXT));
    return true;
  }

  public String getInitialWhitelist() {
    return initial;
  }

  @Override
  public boolean stillValid(Player player) {
    return inventory == null
        || access.getAsBoolean()
            && slot < inventory.getContainerSize()
            && inventory.getItem(slot) == card
            && card.getCount() == 1
            && card.is(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get());
  }

  public String save(ServerPlayer player, String text) {
    if (inventory == null || !stillValid(player)) {
      return "Card changed or access expired. Reopen the editor.";
    }
    long now = player.level().getGameTime();
    if (now < nextSave) {
      return "Please wait a moment before saving again.";
    }
    nextSave = now + SAVE_INTERVAL_TICKS;
    try {
      HuntingCardData.write(card, text);
      inventory.setChanged();
      return "";
    } catch (IllegalArgumentException invalid) {
      return invalid.getMessage();
    }
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }
}
