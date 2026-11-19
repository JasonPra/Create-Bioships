package org.Azkerash.create_bioships.components.logic.stomach;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/** Runtime data belongs to the sublevel component entry, with a portable detached snapshot. */
public final class StomachState
    implements org.Azkerash.create_bioships.core.component.ComponentState {
  int acid = 200, progress, duration, activeSlot = -1, eating, efficiency;
  int longevity;
  String recipeId = "";
  NonNullList<ItemStack> pending = NonNullList.create();

  public CompoundTag save(HolderLookup.Provider registries) {
    var tag = new CompoundTag();
    tag.putInt("Acid", acid);
    var genes = new CompoundTag();
    genes.putInt("Longevity", longevity);
    genes.putInt("Efficiency", efficiency);
    tag.put("Genes", genes);
    tag.putInt("Progress", progress);
    tag.putInt("Duration", duration);
    tag.putInt("ActiveSlot", activeSlot);
    tag.putInt("Eating", eating);
    tag.putString("Recipe", recipeId);
    var outputs = new CompoundTag();
    ContainerHelper.saveAllItems(outputs, pending, registries);
    outputs.putInt("Size", pending.size());
    tag.put("Pending", outputs);
    return tag;
  }

  public void load(CompoundTag tag, HolderLookup.Provider registries) {
    longevity =
        Math.clamp(tag.getCompound("Genes").getInt("Longevity"), 0, StomachGenes.MAX_LONGEVITY);
    efficiency = Math.clamp(tag.getCompound("Genes").getInt("Efficiency"), 0, 3);
    acid =
        tag.contains("Acid") ? Math.clamp(tag.getInt("Acid"), 0, StomachBlockEntity.MAX_ACID) : 200;
    progress = Math.max(0, tag.getInt("Progress"));
    duration = Math.max(0, tag.getInt("Duration"));
    activeSlot = tag.contains("ActiveSlot") ? Math.clamp(tag.getInt("ActiveSlot"), -1, 8) : -1;
    eating = Math.clamp(tag.getInt("Eating"), 0, StomachBlockEntity.EAT_TICKS - 1);
    recipeId = tag.getString("Recipe");
    var outputs = tag.getCompound("Pending");
    pending = NonNullList.withSize(Math.clamp(outputs.getInt("Size"), 0, 32), ItemStack.EMPTY);
    ContainerHelper.loadAllItems(outputs, pending, registries);
  }
}
