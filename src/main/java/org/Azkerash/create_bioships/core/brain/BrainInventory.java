package org.Azkerash.create_bioships.core.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.SubLevelData;

/** A container view of grid data. Shrinking the structure never deletes overflow cards. */
public final class BrainInventory implements Container {
  public static final TagKey<net.minecraft.world.item.Item> CARDS =
      TagKey.create(Registries.ITEM, ResourceLocation.parse("create_bioships:behavior_cards"));
  private final SubLevelBrains brain;

  BrainInventory(SubLevelBrains brain) {
    this.brain = brain;
  }

  public dev.ryanhcode.sable.sublevel.ServerSubLevel body() {
    return brain.body;
  }

  public BlockPos port() {
    return brain.port();
  }

  public int capacity() {
    return brain.capacity();
  }

  public int getContainerSize() {
    return 27;
  }

  public int getMaxStackSize() {
    return 1;
  }

  public boolean isEmpty() {
    return brain.cards.stream().allMatch(ItemStack::isEmpty);
  }

  public ItemStack getItem(int slot) {
    return brain.cards.get(slot);
  }

  public ItemStack removeItem(int slot, int count) {
    var stack = ContainerHelper.removeItem(brain.cards, slot, count);
    if (!stack.isEmpty()) setChanged();
    return stack;
  }

  public ItemStack removeItemNoUpdate(int slot) {
    var stack = ContainerHelper.takeItem(brain.cards, slot);
    if (!stack.isEmpty()) setChanged();
    return stack;
  }

  public void setItem(int slot, ItemStack stack) {
    brain.cards.set(slot, stack);
    stack.limitSize(1);
    setChanged();
  }

  public void setChanged() {
    brain.save();
  }

  public void clearContent() {
    brain.cards.clear();
    setChanged();
  }

  public boolean canPlaceItem(int slot, ItemStack stack) {
    return slot < capacity() && stack.is(CARDS);
  }

  public boolean stillValid(Player player) {
    return validAt(player, brain.port());
  }

  public boolean validAt(Player player, BlockPos port) {
    return port != null
        && port.equals(brain.port())
        && !brain.body.isRemoved()
        && player.level() == brain.body.getLevel()
        && SubLevelData.isLiving(brain.body)
        && !org.Azkerash.create_bioships.core.SubLevelHealth.status(brain.body).dead()
        && org.Azkerash.create_bioships.core.access.SubLevelPermissions.canInspect(
            brain.body, player.getUUID())
        && player
                .getEyePosition()
                .distanceToSqr(brain.body.logicalPose().transformPosition(Vec3.atCenterOf(port)))
            <= 64;
  }
}
