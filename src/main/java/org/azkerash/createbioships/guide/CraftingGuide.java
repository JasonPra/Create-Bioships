package org.azkerash.createbioships.guide;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.core.brain.program.CerebralGuide;

/** Describes actual synchronized recipes, so guide recipes follow datapacks and recipe changes. */
public final class CraftingGuide {
  private static final int MAX_ALTERNATIVE_NAMES = 3;

  private CraftingGuide() {}

  public static List<CerebralGuide.Chapter> chapters(
      RecipeManager manager, HolderLookup.Provider registries) {
    var result = new ArrayList<CerebralGuide.Chapter>();
    for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
      var recipe = holder.value();
      var output = recipe.getResultItem(registries);
      if (output.isEmpty()
          || !BuiltInRegistries.ITEM
              .getKey(output.getItem())
              .getNamespace()
              .equals(CreateBioships.MODID)) {
        continue;
      }
      var legends = new LinkedHashMap<String, Character>();
      var descriptions = new LinkedHashMap<Character, String>();
      var body =
          new StringBuilder("Makes ")
              .append(output.getCount())
              .append(" × ")
              .append(output.getHoverName().getString())
              .append(".\n\n");
      if (recipe instanceof ShapedRecipe shaped) {
        body.append("Crafting table: keep this arrangement. A dot is an empty slot.\n\n");
        for (int y = 0; y < shaped.getHeight(); y++) {
          for (int x = 0; x < shaped.getWidth(); x++) {
            var ingredient = shaped.getIngredients().get(y * shaped.getWidth() + x);
            body.append('[').append(symbol(ingredient, legends, descriptions)).append("] ");
          }
          body.append('\n');
        }
      } else if (recipe instanceof ShapelessRecipe) {
        body.append("Combine in any arrangement, one ingredient per listed slot:\n\n");
        for (var ingredient : recipe.getIngredients()) {
          body.append('[').append(symbol(ingredient, legends, descriptions)).append("] ");
        }
        body.append('\n');
      } else {
        // A specialized crafting serializer may implement additional matching
        // conditions. Do not present its ingredient list as a sufficient recipe.
        continue;
      }
      for (var entry : descriptions.entrySet()) {
        body.append('\n').append(entry.getKey()).append(" = ").append(entry.getValue());
      }
      result.add(
          new CerebralGuide.Chapter(
              "Craft: " + output.getHoverName().getString(), body.toString(), ""));
    }
    result.sort(Comparator.comparing(CerebralGuide.Chapter::title));
    return List.copyOf(result);
  }

  private static char symbol(
      Ingredient ingredient, Map<String, Character> legends, Map<Character, String> descriptions) {
    if (ingredient.isEmpty()) {
      return '.';
    }
    var items = ingredient.getItems();
    var ids =
        Arrays.stream(items)
            .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .sorted()
            .toList();
    String signature = String.join("|", ids);
    var previous = legends.get(signature);
    if (previous != null) {
      return previous;
    }
    char letter = (char) ('A' + legends.size());
    legends.put(signature, letter);
    var names =
        Arrays.stream(items)
            .map(stack -> stack.getHoverName().getString())
            .distinct()
            .limit(MAX_ALTERNATIVE_NAMES)
            .toList();
    String description = String.join(" / ", names);
    if (ids.size() > MAX_ALTERNATIVE_NAMES) {
      description += " / other matching variants";
    }
    descriptions.put(letter, description);
    return letter;
  }
}
