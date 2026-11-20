package org.Azkerash.create_bioships.components.logic.inventory;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.CoreAssemblyRules;

/** Saved inventory and moving-grid interaction distance shared by organ containers. */
public abstract class OrganInventory extends BlockEntity implements Container, MenuProvider {
  private final NonNullList<ItemStack> localItems;
  private java.util.UUID componentId = java.util.UUID.randomUUID();
  private org.Azkerash.create_bioships.core.component.SubLevelComponentStore.Entry binding;
  private dev.ryanhcode.sable.sublevel.ServerSubLevel owner;

  public java.util.UUID componentId() {
    return componentId;
  }

  protected org.Azkerash.create_bioships.core.component.ComponentState localState() {
    return null;
  }

  protected final org.Azkerash.create_bioships.core.component.ComponentState ownedState() {
    bind();
    return binding == null ? localState() : binding.getState();
  }

  public final NonNullList<ItemStack> items() {
    bind();
    return binding == null ? localItems : binding.getItems();
  }

  private void bind() {
    if (isRemoved() || !(level instanceof ServerLevel server)) {
      return;
    }
    var body = CoreAssemblyRules.containing(server, worldPosition);
    if (body == null || body == owner && binding != null) {
      return;
    }
    binding =
        org.Azkerash.create_bioships
            .core
            .component
            .SubLevelComponentStore
            .get(body)
            .bind(
                componentId,
                worldPosition,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(getBlockState().getBlock())
                    .toString(),
                binding == null ? localItems : binding.getItems(),
                localState());
    owner = body;
    componentId = binding.getId();
  }

  @Override
  public void onLoad() {
    super.onLoad();
    bind();
  }

  @Override
  public void setChanged() {
    super.setChanged();
    if (owner != null && !owner.isRemoved()) {
      org.Azkerash.create_bioships.core.component.SubLevelComponentStore.get(owner).dirty();
    }
  }

  protected OrganInventory(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
    super(type, pos, state);
    localItems = NonNullList.withSize(size, ItemStack.EMPTY);
  }

  public int getContainerSize() {
    return items().size();
  }

  public boolean isEmpty() {
    return items().stream().allMatch(ItemStack::isEmpty);
  }

  public ItemStack getItem(int slot) {
    return items().get(slot);
  }

  public ItemStack removeItem(int slot, int count) {
    var result = ContainerHelper.removeItem(items(), slot, count);
    if (!result.isEmpty()) {
      setChanged();
    }
    return result;
  }

  public ItemStack removeItemNoUpdate(int slot) {
    return ContainerHelper.takeItem(items(), slot);
  }

  public void setItem(int slot, ItemStack stack) {
    items().set(slot, stack);
    stack.limitSize(getMaxStackSize(stack));
    setChanged();
  }

  public void clearContent() {
    items().clear();
    setChanged();
  }

  public boolean stillValid(Player player) {
    if (level == null || level.getBlockEntity(worldPosition) != this) {
      return false;
    }
    var target = Vec3.atCenterOf(worldPosition);
    if (level instanceof ServerLevel server) {
      var body = CoreAssemblyRules.containing(server, worldPosition);
      if (body != null) {
        target = body.logicalPose().transformPosition(target);
      }
    }
    return player.getEyePosition().distanceToSqr(target) <= 64;
  }

  public boolean canPlaceItem(int slot, ItemStack stack) {
    return false;
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    ContainerHelper.saveAllItems(tag, items(), registries);
    tag.putUUID("ComponentInstance", componentId);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.hasUUID("ComponentInstance") && !componentId.equals(tag.getUUID("ComponentInstance"))) {
      if (owner != null && binding != null) {
        org.Azkerash.create_bioships.core.component.SubLevelComponentStore.release(
            owner, binding.getId(), worldPosition);
      }
      binding = null;
      owner = null;
      componentId = tag.getUUID("ComponentInstance");
    }
    (binding == null ? localItems : binding.getItems()).clear();
    ContainerHelper.loadAllItems(
        tag, binding == null ? localItems : binding.getItems(), registries);
  }

  /** One insertion implementation for transport, with an explicit simulation phase. */
  public ItemStack insert(ItemStack incoming, int first, int end, boolean simulate) {
    var rest = incoming.copy();
    for (int pass = 0; pass < 2; pass++) {
      for (int i = first; i < end && !rest.isEmpty(); i++) {
        var current = items().get(i);
        if (pass == 0 && current.isEmpty() || pass == 1 && !current.isEmpty()) {
          continue;
        }
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, rest)) {
          continue;
        }
        int amount =
            Math.min(
                rest.getCount(),
                Math.min(rest.getMaxStackSize(), getMaxStackSize()) - current.getCount());
        if (amount <= 0) {
          continue;
        }
        if (!simulate) {
          if (current.isEmpty()) {
            items().set(i, rest.copyWithCount(amount));
          } else {
            current.grow(amount);
          }
          setChanged();
        }
        rest.shrink(amount);
      }
    }
    return rest;
  }
}
