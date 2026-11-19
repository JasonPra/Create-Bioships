package org.Azkerash.create_bioships.components.logic.stomach;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Food and component slots are inspect-only; the reserved paste cell is player-accessible. */
public final class StomachMenu extends AbstractContainerMenu {
  private final Container stomach;
  private final ContainerData data;

  public StomachMenu(int id, Inventory inventory, StomachBlockEntity stomach) {
    this(id, inventory, stomach, stomach.menuData());
  }

  public static StomachMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new StomachMenu(id, inventory, new SimpleContainer(27), new SimpleContainerData(5));
  }

  private StomachMenu(int id, Inventory inventory, Container stomach, ContainerData data) {
    super(ComponentRegistry.STOMACH_MENU.get(), id);
    this.stomach = stomach;
    this.data = data;
    addDataSlots(data);
    for (int i = 0; i < 27; i++) {
      int x = i < 9 ? 12 + (i % 3) * 18 : 104 + ((i - 9) % 6) * 18;
      int y = 96 + (i < 9 ? i / 3 : (i - 9) / 6) * 18;
      addSlot(
          new Slot(stomach, i, x, y) {
            public boolean mayPlace(ItemStack stack) {
              return getContainerSlot() == StomachBlockEntity.PASTE_SLOT
                  && stack.is(StomachBlockEntity.paste());
            }

            public boolean mayPickup(Player player) {
              return getContainerSlot() == StomachBlockEntity.PASTE_SLOT;
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 40 + col * 18, 176 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 40 + col * 18, 234));
  }

  public Container pasteIntake() {
    return stomach;
  }

  public int value(int index) {
    return data.get(index);
  }

  public ItemStack quickMoveStack(Player player, int slot) {
    if (slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
    var source = slots.get(slot);
    if (!source.hasItem()) return ItemStack.EMPTY;
    var current = source.getItem();
    var original = current.copy();
    if (slot == StomachBlockEntity.PASTE_SLOT) {
      if (!moveItemStackTo(current, 27, slots.size(), true)) return ItemStack.EMPTY;
    } else if (slot >= 27 && current.is(StomachBlockEntity.paste())) {
      if (!moveItemStackTo(
          current, StomachBlockEntity.PASTE_SLOT, StomachBlockEntity.PASTE_SLOT + 1, false))
        return ItemStack.EMPTY;
    } else return ItemStack.EMPTY;
    if (current.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
    else source.setChanged();
    source.onTake(player, current);
    return original;
  }

  public boolean stillValid(Player player) {
    return stomach.stillValid(player);
  }
}
