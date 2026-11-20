package org.Azkerash.create_bioships.components.logic.stomach;

import static org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity.*;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import org.Azkerash.create_bioships.core.*;

/** Stomach-specific simulation invoked centrally by the sublevel controller. */
public final class StomachDigestion {
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
    if (level.getGameTime() % 20 == 0) {
      level.sendBlockUpdated(worldPosition, stomach.getBlockState(), stomach.getBlockState(), 2);
    }
    var body = CoreAssemblyRules.containing(server, worldPosition);
    if (body == null || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) {
      return;
    }
    stomach.normalizePasteSlot();
    if (state.acid < MAX_ACID) {
      state.acid++;
      stomach.setChanged();
    }
    if (SubLevelData.hunger(body) < SubLevelData.maximumHunger(body)) {
      if (pasteCount(stomach) >= PASTE_PER_MEAL) {
        if (++state.eating >= EAT_TICKS) {
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
          int nutrition = genes.nutritionFifths() + state.nutritionRemainder;
          SubLevelData.hunger(body, SubLevelData.hunger(body) + nutrition / 5);
          state.nutritionRemainder = nutrition % 5;
          state.eating = 0;
        }
        stomach.setChanged();
      } else {
        state.eating = 0;
      }
    } else {
      state.eating = 0;
    }
    if (state.activeSlot < 0) {
      for (int slot = 0; slot < INPUT_SLOTS; slot++) {
        var match = recipe(level, items.get(slot));
        if (match.isEmpty()) {
          continue;
        }
        var value = match.get().value();
        if (items.get(slot).getCount() < value.getIngredientQuantity().count()) {
          continue;
        }
        int cost = Math.clamp(value.getCraftingCostNutrients() * 100, 50, MAX_ACID);
        if (state.acid < cost) {
          continue;
        }
        state.acid -= cost;
        state.activeSlot = slot;
        state.recipeId = match.get().id().toString();
        state.duration = Math.max(1, value.getCraftingTimeTicks());
        state.progress = 0;
        state.pending = NonNullList.create();
        for (var output : value.getOutputs()) {
          var result = output.getItemStack(level.random);
          if (!result.isEmpty()) {
            state.pending.add(result.copy());
          }
        }
        genes.addExtraComponent(state.pending, level.random);
        if (items.get(slot).has(DataComponents.FOOD) && !items.get(slot).is(paste())) {
          state.pending.add(new ItemStack(paste(), genes.pastePerMeal()));
        }
        stomach.setChanged();
        break;
      }
    }
    if (state.activeSlot < 0) {
      return;
    }
    var match = recipe(level, items.get(state.activeSlot));
    if (match.isEmpty()
        || !match.get().id().toString().equals(state.recipeId)
        || items.get(state.activeSlot).getCount()
            < match.get().value().getIngredientQuantity().count()) {
      // Inputs are read-only during processing. Commands invalidating a recipe cancel it without
      // outputs.
      state.activeSlot = -1;
      state.progress = state.duration = 0;
      state.pending = NonNullList.create();
      stomach.setChanged();
      return;
    }
    if (state.progress < state.duration) {
      state.progress++;
      stomach.setChanged();
    }
    if (state.progress < state.duration) {
      return;
    }
    var snapshot = NonNullList.withSize(items.size(), ItemStack.EMPTY);
    for (int i = 0; i < items.size(); i++) {
      snapshot.set(i, items.get(i).copy());
    }
    boolean fits = true;
    for (var output : state.pending) {
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
    boolean rotten = items.get(state.activeSlot).is(Items.ROTTEN_FLESH);
    items.get(state.activeSlot).shrink(match.get().value().getIngredientQuantity().count());
    if (rotten) {
      org.Azkerash.create_bioships.core.digestion.SubLevelSickness.infect(body);
    }
    state.activeSlot = -1;
    state.progress = state.duration = 0;
    state.pending = NonNullList.create();
    stomach.setChanged();
  }
}
