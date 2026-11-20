package org.azkerash.createbioships.components.logic.stomach;

import static org.azkerash.createbioships.components.logic.stomach.StomachBlockEntity.*;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import org.azkerash.createbioships.core.*;

/** Stomach-specific simulation invoked centrally by the sublevel controller. */
public final class StomachDigestion {
  private static final int CLIENT_SYNC_INTERVAL_TICKS = 20;
  private static final int ACID_PER_RECIPE_NUTRIENT = 100;
  private static final int MIN_RECIPE_ACID_COST = 50;

  private StomachDigestion() {}

  static int pasteCount(StomachBlockEntity stomach) {
    int count = 0;
    for (int i = INPUT_SLOTS; i < stomach.items().size(); i++) {
      if (stomach.getItem(i).is(paste())) {
        count += stomach.getItem(i).getCount();
      }
    }
    return count;
  }

  public static void advance(StomachBlockEntity stomach) {
    var level = stomach.getLevel();
    var worldPosition = stomach.getBlockPos();
    var state = stomach.state();
    var items = stomach.items();
    var genes = stomach.genes();
    if (!(level instanceof ServerLevel server)) {
      return;
    }
    if (level.getGameTime() % CLIENT_SYNC_INTERVAL_TICKS == 0) {
      level.sendBlockUpdated(
          worldPosition, stomach.getBlockState(), stomach.getBlockState(), Block.UPDATE_CLIENTS);
    }
    var body = CoreAssemblyRules.containing(server, worldPosition);
    if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) {
      return;
    }
    stomach.normalizePasteSlot();
    if (state.getAcid() < MAX_ACID) {
      state.incrementAcid();
      stomach.setChanged();
    }
    if (SubLevelData.hunger(body) < SubLevelData.maximumHunger(body)) {
      if (pasteCount(stomach) >= PASTE_PER_MEAL) {
        if (state.incrementAndGetEating() >= EAT_TICKS) {
          int remaining = PASTE_PER_MEAL;
          for (int offset = 0; offset < items.size() - INPUT_SLOTS && remaining > 0; offset++) {
            int i = offset == 0 ? PASTE_SLOT : INPUT_SLOTS + offset - 1;
            var stack = items.get(i);
            if (!stack.is(paste())) {
              continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
          }
          int nutrition = genes.nutritionFifths() + state.getNutritionRemainder();
          SubLevelData.hunger(
              body,
              SubLevelData.hunger(body) + nutrition / StomachGenes.NUTRITION_UNITS_PER_HUNGER);
          state.setNutritionRemainder(nutrition % StomachGenes.NUTRITION_UNITS_PER_HUNGER);
          state.setEating(0);
        }
        stomach.setChanged();
      } else {
        state.setEating(0);
      }
    } else {
      state.setEating(0);
    }
    if (state.getActiveSlot() < 0) {
      for (int slot = 0; slot < INPUT_SLOTS; slot++) {
        var match = recipe(level, items.get(slot));
        if (match.isEmpty()) {
          continue;
        }
        var value = match.get().value();
        if (items.get(slot).getCount() < value.getIngredientQuantity().count()) {
          continue;
        }
        int cost =
            Math.clamp(
                value.getCraftingCostNutrients() * ACID_PER_RECIPE_NUTRIENT,
                MIN_RECIPE_ACID_COST,
                MAX_ACID);
        if (state.getAcid() < cost) {
          continue;
        }
        state.setAcid(state.getAcid() - cost);
        state.setActiveSlot(slot);
        state.setRecipeId(match.get().id().toString());
        state.setDuration(Math.max(1, value.getCraftingTimeTicks()));
        state.setProgress(0);
        state.setPending(NonNullList.create());
        for (var output : value.getOutputs()) {
          var result = output.getItemStack(level.random);
          if (!result.isEmpty()) {
            state.getPending().add(result.copy());
          }
        }
        genes.addExtraComponent(state.getPending(), level.random);
        if (items.get(slot).has(DataComponents.FOOD) && !items.get(slot).is(paste())) {
          state.getPending().add(new ItemStack(paste(), genes.pastePerMeal()));
        }
        stomach.setChanged();
        break;
      }
    }
    if (state.getActiveSlot() < 0) {
      return;
    }
    var match = recipe(level, items.get(state.getActiveSlot()));
    if (match.isEmpty()
        || !match.get().id().toString().equals(state.getRecipeId())
        || items.get(state.getActiveSlot()).getCount()
            < match.get().value().getIngredientQuantity().count()) {
      // Inputs are read-only during processing. Commands invalidating a recipe cancel it without
      // outputs.
      state.setActiveSlot(-1);
      state.setDuration(0);
      state.setProgress(0);
      state.setPending(NonNullList.create());
      stomach.setChanged();
      return;
    }
    if (state.getProgress() < state.getDuration()) {
      state.incrementProgress();
      stomach.setChanged();
    }
    if (state.getProgress() < state.getDuration()) {
      return;
    }
    var snapshot = NonNullList.withSize(items.size(), ItemStack.EMPTY);
    for (int i = 0; i < items.size(); i++) {
      snapshot.set(i, items.get(i).copy());
    }
    boolean fits = true;
    for (var output : state.getPending()) {
      if (!stomach.insert(output, INPUT_SLOTS, items.size(), false).isEmpty()) {
        fits = false;
        break;
      }
    }
    if (!fits) {
      for (int i = 0; i < items.size(); i++) {
        items.set(i, snapshot.get(i));
      }
      return;
    }
    boolean rotten = items.get(state.getActiveSlot()).is(Items.ROTTEN_FLESH);
    items.get(state.getActiveSlot()).shrink(match.get().value().getIngredientQuantity().count());
    if (rotten) {
      org.azkerash.createbioships.core.digestion.SubLevelSickness.infect(body);
    }
    state.setActiveSlot(-1);
    state.setDuration(0);
    state.setProgress(0);
    state.setPending(NonNullList.create());
    stomach.setChanged();
  }
}
