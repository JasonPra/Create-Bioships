package org.azkerash.createbioships.core;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.brain.program.CerebralGuide;
import org.azkerash.createbioships.guide.CraftingGuide;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class BioshipGuideTests {
  @GameTest(template = "test_empty")
  public static void every_ordinary_item_is_craftable_and_general_guide_uses_loaded_recipes(
      GameTestHelper h) {
    var level = h.getLevel();
    var craftable = new HashSet<Item>();
    int recipes = 0;
    for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
      var output = holder.value().getResultItem(level.registryAccess());
      craftable.add(output.getItem());
      if (BuiltInRegistries.ITEM
          .getKey(output.getItem())
          .getNamespace()
          .equals("create_bioships")) {
        recipes++;
      }
    }
    for (var id : BuiltInRegistries.ITEM.keySet()) {
      if (id.getNamespace().equals("create_bioships")
          && !Set.of("development_toolkit", "development_navigator").contains(id.getPath())) {
        h.assertTrue(
            craftable.contains(BuiltInRegistries.ITEM.get(id)),
            "Missing crafting recipe for " + id);
      }
    }
    var chapters = CraftingGuide.chapters(level.getRecipeManager(), level.registryAccess());
    h.assertTrue(
        chapters.size() == recipes && recipes >= 40, "Guide omitted loaded mod crafting recipes");
    h.assertTrue(
        chapters.stream().anyMatch(chapter -> chapter.body().contains("[.]")),
        "Shaped empty slots not documented");
    h.assertTrue(
        chapters.stream().anyMatch(chapter -> chapter.body().contains("any arrangement")),
        "Shapeless recipes not documented");
    try (var stream =
        BioshipGuideTests.class.getResourceAsStream(
            "/assets/create_bioships/guide/bioships.json")) {
      h.assertTrue(stream != null, "General guide resource missing");
      var guide = CerebralGuide.read(new InputStreamReader(stream, StandardCharsets.UTF_8));
      h.assertTrue(
          guide.size() >= 12
              && guide.stream().anyMatch(chapter -> chapter.title().equals("Your first creature")),
          "General guide lacks getting-started instructions");
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
    h.succeed();
  }
}
