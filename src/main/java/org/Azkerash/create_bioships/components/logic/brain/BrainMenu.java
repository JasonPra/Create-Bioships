package org.Azkerash.create_bioships.components.logic.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.brain.BrainInventory;

/** Fixed backing storage with a changing usable capacity. Overflow remains removable. */
public final class BrainMenu extends AbstractContainerMenu {
  private final Container memory;
  private final ContainerData data;
  private final java.util.function.Predicate<Player> valid;

  public BrainMenu(int id, Inventory player, BrainInventory memory, BlockPos port) {
    this(
        id,
        player,
        memory,
        new ContainerData() {
          public int get(int i) {
            return memory.capacity();
          }

          public void set(int i, int v) {}

          public int getCount() {
            return 1;
          }
        },
        p -> memory.validAt(p, port));
  }

  public static BrainMenu fromNetwork(int id, Inventory player, RegistryFriendlyByteBuf buffer) {
    return new BrainMenu(
        id, player, new SimpleContainer(27), new SimpleContainerData(1), p -> true);
  }

  private BrainMenu(
      int id,
      Inventory player,
      Container memory,
      ContainerData data,
      java.util.function.Predicate<Player> valid) {
    super(ComponentRegistry.BRAIN_MENU.get(), id);
    this.memory = memory;
    this.data = data;
    this.valid = valid;
    addDataSlots(data);
    for (int i = 0; i < 27; i++) {
      final int slot = i;
      addSlot(
          new Slot(memory, i, 33 + (i % 9) * 18, 48 + (i / 9) * 18) {
            public boolean mayPlace(ItemStack stack) {
              return slot < capacity() && stack.is(BrainInventory.CARDS);
            }

            public int getMaxStackSize() {
              return 1;
            }

            @Override
            public void onTake(Player actor, ItemStack stack) {
              org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.remember(
                  actor, stack);
              super.onTake(actor, stack);
            }

            @Override
            public void setByPlayer(ItemStack stack) {
              org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.restore(
                  player.player, stack);
              super.setByPlayer(stack);
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(player, col + row * 9 + 9, 33 + col * 18, 126 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 33 + col * 18, 184));
  }

  public BrainInventory serverMemory() {
    return memory instanceof BrainInventory brain ? brain : null;
  }

  public int capacity() {
    return Math.clamp(data.get(0), 0, 27);
  }

  public boolean stillValid(Player player) {
    return valid.test(player);
  }

  public ItemStack quickMoveStack(Player player, int index) {
    if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    var slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    var current = slot.getItem();
    var original = current.copy();
    if (index < 27) {
      if (!moveItemStackTo(current, 27, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!current.is(BrainInventory.CARDS) || !moveItemStackTo(current, 0, 27, false))
      return ItemStack.EMPTY;
    if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(player, current);
    return original;
  }
}
