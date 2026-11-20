package org.Azkerash.create_bioships.components.logic.nametag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;

public final class NameTagDisplayMenu extends AbstractContainerMenu {
  private final Container inventory;
  public final NameTagDisplayEntity display;
  public final ContainerData offsets;

  public NameTagDisplayMenu(int id, Inventory player, NameTagDisplayEntity display) {
    this(
        id,
        player,
        display,
        display,
        new ContainerData() {
          public int get(int i) {
            var o = display.offset();
            return i == 0 ? o.x : i == 1 ? o.y : o.z;
          }

          public void set(int i, int v) {}

          public int getCount() {
            return 3;
          }
        });
  }

  private NameTagDisplayMenu(
      int id, Inventory player, Container inv, NameTagDisplayEntity display, ContainerData data) {
    super(ComponentRegistry.NAME_TAG_DISPLAY_MENU.get(), id);
    this.inventory = inv;
    this.display = display;
    this.offsets = data;
    addSlot(
        new Slot(inv, 0, 106, 43) {
          public boolean mayPlace(ItemStack stack) {
            return NameTagDisplayEntity.accepts(stack);
          }

          public int getMaxStackSize() {
            return 1;
          }
        });
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        addSlot(new Slot(player, col + row * 9 + 9, 33 + col * 18, 160 + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      addSlot(new Slot(player, col, 33 + col * 18, 218));
    }
    addDataSlots(data);
  }

  public static NameTagDisplayMenu fromNetwork(
      int id, Inventory player, RegistryFriendlyByteBuf b) {
    var data = new SimpleContainerData(3);
    for (int i = 0; i < 3; i++) {
      data.set(i, b.readVarInt());
    }
    return new NameTagDisplayMenu(id, player, new SimpleContainer(1), null, data);
  }

  @Override
  public boolean stillValid(Player player) {
    return inventory.stillValid(player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size()) {
      return ItemStack.EMPTY;
    }
    var slot = slots.get(index);
    if (!slot.hasItem()) {
      return ItemStack.EMPTY;
    }
    var stack = slot.getItem();
    var original = stack.copy();
    if (index == 0) {
      if (!moveItemStackTo(stack, 1, slots.size(), true)) {
        return ItemStack.EMPTY;
      }
    } else if (!NameTagDisplayEntity.accepts(stack) || !moveItemStackTo(stack, 0, 1, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) {
      slot.setByPlayer(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    slot.onTake(player, stack);
    return original;
  }
}
