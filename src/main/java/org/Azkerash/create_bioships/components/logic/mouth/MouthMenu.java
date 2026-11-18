package org.Azkerash.create_bioships.components.logic.mouth;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Native 3x3 menu/screen, with authoritative food validation on intake slots. */
public final class MouthMenu extends DispenserMenu {
  public MouthMenu(int id, Inventory inventory, MouthBlockEntity mouth) {
    super(id, inventory, mouth);
  }

  @Override
  protected Slot addSlot(Slot original) {
    if (slots.size() < 9)
      return super.addSlot(
          new Slot(original.container, original.getContainerSlot(), original.x, original.y) {
            public boolean mayPlace(ItemStack stack) {
              return container.canPlaceItem(getContainerSlot(), stack);
            }
          });
    return super.addSlot(original);
  }
}
