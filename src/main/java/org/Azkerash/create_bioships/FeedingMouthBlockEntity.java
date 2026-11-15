package org.Azkerash.create_bioships;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FeedingMouthBlockEntity extends BaseContainerBlockEntity {
  private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
  private int inheritedBiomass;
  private boolean inheritedSlot, embeddedInHull;

  public FeedingMouthBlockEntity(BlockPos pos, BlockState state) {
    super(Create_bioships.FEEDING_MOUTH_ENTITY.get(), pos, state);
  }

  @Override
  public int getContainerSize() {
    return 9;
  }

  @Override
  protected NonNullList<ItemStack> getItems() {
    return items;
  }

  @Override
  protected void setItems(NonNullList<ItemStack> items) {
    this.items = items;
  }

  @Override
  protected Component getDefaultName() {
    return Component.translatable("block.create_bioships.feeding_mouth");
  }

  @Override
  protected AbstractContainerMenu createMenu(int id, Inventory player) {
    return new ChestMenu(MenuType.GENERIC_9x1, id, player, this, 1);
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    if (slot == 0 && (items.get(0).isEmpty() || stack.isEmpty())) inheritedSlot = false;
    super.setItem(slot, stack);
  }

  @Override
  public boolean stillValid(Player player) {
    if (level == null || level.getBlockEntity(worldPosition) != this) return false;
    var ship = BioshipIntegration.containing(level, worldPosition);
    Vec3 target = Vec3.atCenterOf(worldPosition);
    if (ship != null) target = ship.logicalPose().transformPosition(target);
    return player.getEyePosition().distanceToSqr(target) <= 64;
  }

  public void inheritBioForge(ItemStack stack, int biomass) {
    items.set(0, stack);
    inheritedSlot = !stack.isEmpty();
    inheritedBiomass = Math.max(0, biomass);
    setChanged();
  }

  public boolean embeddedInHull() {
    return embeddedInHull;
  }

  public void embeddedInHull(boolean value) {
    embeddedInHull = value;
    setChanged();
  }

  public static void tick(
      Level level, BlockPos pos, BlockState state, FeedingMouthBlockEntity mouth) {
    if (level.getGameTime() % 20 != 0) return;
    var ship = BioshipIntegration.containing(level, pos);
    if (ship == null) return;
    for (var actor : ship.getPlot().getBlockEntityActors())
      if (actor instanceof LivingCoreBlockEntity core && core.primary(ship)) {
        var stomach = DigestiveSystem.stomach(core);
        if (stomach == null || !stomach.connected(core)) return;
        if (mouth.inheritedBiomass > 0) {
          stomach.retainFlesh(mouth.inheritedBiomass);
          mouth.inheritedBiomass = 0;
          mouth.setChanged();
        }
        for (int slot = 0; slot < mouth.items.size(); slot++) {
          ItemStack stack = mouth.items.get(slot);
          if (stack.isEmpty()) {
            if (slot == 0) mouth.inheritedSlot = false;
            continue;
          }
          boolean oldStock = slot == 0 && mouth.inheritedSlot;
          if (stack.is(BioshipIntegration.constructionMaterial())
              && !LivingCoreBlockEntity.isMeat(stack)) {
            stomach.retain(stack.copy());
            mouth.items.set(slot, ItemStack.EMPTY);
            mouth.setChanged();
            continue;
          }
          if (DigestiveSystem.offerFood(core, stack, oldStock)) {
            stack.shrink(1);
            mouth.setChanged();
            break;
          }
          if (!core.acceptAmalgam(stack)) continue;
          ItemStack remainder = stack.getCraftingRemainingItem();
          stack.shrink(1);
          if (!remainder.isEmpty()) {
            if (stack.isEmpty()) mouth.items.set(slot, remainder);
            else {
              int empty = -1;
              for (int i = 0; i < mouth.items.size(); i++)
                if (mouth.items.get(i).isEmpty()) {
                  empty = i;
                  break;
                }
              if (empty >= 0) mouth.items.set(empty, remainder);
              else
                Containers.dropItemStack(
                    level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, remainder);
            }
          }
          mouth.setChanged();
          break;
        }
        return;
      }
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    ContainerHelper.saveAllItems(tag, items, registries);
    tag.putInt("InheritedBiomass", inheritedBiomass);
    tag.putBoolean("InheritedSlot", inheritedSlot);
    tag.putBoolean("EmbeddedInHull", embeddedInHull);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    items = NonNullList.withSize(9, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(tag, items, registries);
    inheritedBiomass = Math.clamp(tag.getInt("InheritedBiomass"), 0, 1000000);
    inheritedSlot = tag.getBoolean("InheritedSlot");
    embeddedInHull = tag.getBoolean("EmbeddedInHull");
  }
}
