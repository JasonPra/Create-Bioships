package org.Azkerash.create_bioships.components.logic.mouth;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Nine food intake slots and native player inventory, validated on both sides. */
public final class MouthMenu extends AbstractContainerMenu {
  private final Container mouth;

  public MouthMenu(int id, Inventory inventory, MouthBlockEntity mouth) {
    this(id, inventory, (Container) mouth);
  }

  public static MouthMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new MouthMenu(id, inventory, new SimpleContainer(9));
  }

  private MouthMenu(int id, Inventory inventory, Container mouth) {
    super(ComponentRegistry.MOUTH_MENU.get(), id);
    this.mouth = mouth;
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 3; col++) {
        addSlot(
            new Slot(mouth, row * 3 + col, 87 + col * 18, 54 + row * 18) {
              public boolean mayPlace(ItemStack stack) {
                return MouthBlockEntity.accepts(inventory.player.level(), stack);
              }
            });
      }
    }
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        addSlot(new Slot(inventory, col + row * 9 + 9, 33 + col * 18, 140 + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      addSlot(new Slot(inventory, col, 33 + col * 18, 198));
    }
  }

  public Container intake() {
    return mouth;
  }

  public boolean stillValid(Player player) {
    return mouth.stillValid(player);
  }

  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) {
      return ItemStack.EMPTY;
    }
    var slot = slots.get(index);
    if (!slot.hasItem()) {
      return ItemStack.EMPTY;
    }
    var current = slot.getItem();
    var original = current.copy();
    if (index < 9) {
      if (!moveItemStackTo(current, 9, slots.size(), true)) {
        return ItemStack.EMPTY;
      }
    } else if (!MouthBlockEntity.accepts(player.level(), current)
        || !moveItemStackTo(current, 0, 9, false)) {
      return ItemStack.EMPTY;
    }
    if (current.isEmpty()) {
      slot.setByPlayer(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    slot.onTake(player, current);
    return original;
  }
}
