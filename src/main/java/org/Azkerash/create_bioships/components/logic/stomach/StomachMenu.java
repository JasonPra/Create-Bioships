package org.Azkerash.create_bioships.components.logic.stomach;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;

/** Internal slots are inspect-only; neither clicks nor shift-click can feed the stomach. */
public final class StomachMenu extends AbstractContainerMenu {
  private final Container stomach;
  private final ContainerData data;

  public StomachMenu(int id, Inventory inventory, StomachBlockEntity stomach) {
    this(id, inventory, stomach, stomach.menuData());
  }

  public static StomachMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new StomachMenu(id, inventory, new SimpleContainer(27), new SimpleContainerData(3));
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
              return false;
            }

            public boolean mayPickup(Player player) {
              return false;
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 40 + col * 18, 176 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 40 + col * 18, 234));
  }

  public int value(int index) {
    return data.get(index);
  }

  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  public boolean stillValid(Player player) {
    return stomach.stillValid(player);
  }
}
