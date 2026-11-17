package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.registry.Create_bioships;

public class StomachMenu extends AbstractContainerMenu {
  private final ContainerData data;
  private final BlockPos pos;
  private final StomachInventoryView view;

  public StomachMenu(int id, Inventory inventory, StomachBlockEntity stomach) {
    this(id, inventory, stomach.getBlockPos(), new StomachInventoryView(stomach));
  }

  private StomachMenu(int id, Inventory inventory, BlockPos pos, StomachInventoryView view) {
    this(id, inventory, pos, view.data(), view, view);
  }

  private StomachMenu(
      int id,
      Inventory inventory,
      BlockPos pos,
      ContainerData data,
      Container food,
      StomachInventoryView view) {
    super(Create_bioships.STOMACH_MENU.get(), id);
    this.pos = pos;
    this.data = data;
    this.view = view;
    addDataSlots(data);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(
            new Slot(food, row * 9 + column, 29 + column * 18, 112 + row * 18) {
              public boolean mayPickup(Player player) {
                return false;
              }

              public boolean mayPlace(ItemStack stack) {
                return false;
              }
            });
  }

  public static StomachMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new StomachMenu(
        id,
        inventory,
        buffer.readBlockPos(),
        new SimpleContainerData(StomachInventoryView.FIELDS * 2),
        new SimpleContainer(27),
        null);
  }

  public int value(int index) {
    return data.get(index * 2) & 65535 | (data.get(index * 2 + 1) & 65535) << 16;
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    return view != null
        && stillValid(player)
        && (button == 0 || button == 1)
        && view.turn(button == 0 ? -1 : 1);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    if (view != null && !view.stillValid(player)) return false;
    if (!player.level().getBlockState(pos).is(Create_bioships.STOMACH.get())) return false;
    var ship = BioshipIntegration.containing(player.level(), pos);
    Vec3 target = Vec3.atCenterOf(pos);
    if (ship != null) target = ship.logicalPose().transformPosition(target);
    return player.getEyePosition().distanceToSqr(target) <= 64;
  }
}
