package org.Azkerash.create_bioships.components.logic.mouth;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.inventory.OrganInventory;
import org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity;

/** Food intake only. Digestion belongs to stomachs, routing to intestine transport. */
public final class MouthBlockEntity extends OrganInventory {
  public MouthBlockEntity(BlockPos pos, BlockState state) {
    super(ComponentRegistry.MOUTH_ENTITY.get(), pos, state, 9);
  }

  public boolean canPlaceItem(int slot, ItemStack stack) {
    return level != null && StomachBlockEntity.accepts(level, stack);
  }

  public ItemStack feed(ItemStack stack, boolean simulate) {
    return canPlaceItem(0, stack) ? insert(stack, 0, 9, simulate) : stack.copy();
  }

  public Component getDisplayName() {
    return Component.translatable("block.create_bioships.feeding_mouth");
  }

  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new MouthMenu(id, inventory, this);
  }
}
