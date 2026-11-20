package org.azkerash.createbioships.components.logic.stomach;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/** Runtime data belongs to the sublevel component entry, with a portable detached snapshot. */
public final class StomachState
    implements org.azkerash.createbioships.core.component.ComponentState {
  private static final int INITIAL_ACID = 200;
  private static final int MAX_PENDING_OUTPUT_STACKS = 32;

  private int acid = INITIAL_ACID, progress, duration, activeSlot = -1, eating, efficiency;
  private int longevity, nutritionRemainder;
  private String recipeId = "";
  private NonNullList<ItemStack> pending = NonNullList.create();

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
    tag.putInt("NutritionRemainder", nutritionRemainder);
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
    efficiency =
        Math.clamp(tag.getCompound("Genes").getInt("Efficiency"), 0, StomachGenes.MAX_EFFICIENCY);
    acid =
        tag.contains("Acid")
            ? Math.clamp(tag.getInt("Acid"), 0, StomachBlockEntity.MAX_ACID)
            : INITIAL_ACID;
    progress = Math.max(0, tag.getInt("Progress"));
    duration = Math.max(0, tag.getInt("Duration"));
    activeSlot =
        tag.contains("ActiveSlot")
            ? Math.clamp(tag.getInt("ActiveSlot"), -1, StomachBlockEntity.INPUT_SLOTS - 1)
            : -1;
    eating = Math.clamp(tag.getInt("Eating"), 0, StomachBlockEntity.EAT_TICKS - 1);
    nutritionRemainder =
        Math.clamp(
            tag.getInt("NutritionRemainder"), 0, StomachGenes.NUTRITION_UNITS_PER_HUNGER - 1);
    recipeId = tag.getString("Recipe");
    var outputs = tag.getCompound("Pending");
    pending =
        NonNullList.withSize(
            Math.clamp(outputs.getInt("Size"), 0, MAX_PENDING_OUTPUT_STACKS), ItemStack.EMPTY);
    ContainerHelper.loadAllItems(outputs, pending, registries);
  }

  public int getAcid() {
    return this.acid;
  }

  public void setAcid(int value) {
    this.acid = value;
  }

  public void incrementAcid() {
    this.acid++;
  }

  public int getProgress() {
    return this.progress;
  }

  public void setProgress(int value) {
    this.progress = value;
  }

  public void incrementProgress() {
    this.progress++;
  }

  public int getDuration() {
    return this.duration;
  }

  public void setDuration(int value) {
    this.duration = value;
  }

  public int getActiveSlot() {
    return this.activeSlot;
  }

  public void setActiveSlot(int value) {
    this.activeSlot = value;
  }

  public int getEating() {
    return this.eating;
  }

  public void setEating(int value) {
    this.eating = value;
  }

  public int incrementAndGetEating() {
    return ++this.eating;
  }

  public int getEfficiency() {
    return this.efficiency;
  }

  public void setEfficiency(int value) {
    this.efficiency = value;
  }

  public void incrementEfficiency() {
    this.efficiency++;
  }

  public int getLongevity() {
    return this.longevity;
  }

  public void setLongevity(int value) {
    this.longevity = value;
  }

  public void incrementLongevity() {
    this.longevity++;
  }

  public int getNutritionRemainder() {
    return this.nutritionRemainder;
  }

  public void setNutritionRemainder(int value) {
    this.nutritionRemainder = value;
  }

  public String getRecipeId() {
    return this.recipeId;
  }

  public void setRecipeId(String value) {
    this.recipeId = value;
  }

  public NonNullList<ItemStack> getPending() {
    return this.pending;
  }

  public void setPending(NonNullList<ItemStack> value) {
    this.pending = value;
  }
}
