package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class StomachMenu extends AbstractContainerMenu {
  private final ContainerData data;
  private final BlockPos pos;

  public StomachMenu(int id, Inventory inventory, StomachBlockEntity stomach) {
    this(id, inventory, stomach.getBlockPos(), stomach.menuData());
  }

  private StomachMenu(int id, Inventory inventory, BlockPos pos, ContainerData data) {
    super(Create_bioships.STOMACH_MENU.get(), id);
    this.pos = pos;
    this.data = data;
    addDataSlots(data);
  }

  public static StomachMenu fromNetwork(
      int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    return new StomachMenu(id, inventory, buffer.readBlockPos(), new SimpleContainerData(9));
  }

  public int value(int index) {
    return data.get(index);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    if (!player.level().getBlockState(pos).is(Create_bioships.STOMACH.get())) return false;
    var ship = BioshipIntegration.containing(player.level(), pos);
    Vec3 target = Vec3.atCenterOf(pos);
    if (ship != null) target = ship.logicalPose().transformPosition(target);
    return player.getEyePosition().distanceToSqr(target) <= 64;
  }
}
