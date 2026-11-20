package org.azkerash.createbioships.core.brain.hunting;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.azkerash.createbioships.components.ComponentRegistry;

/** The card owns only a prey whitelist; no source text or executable instructions are accepted. */
public final class HuntingCardData {
  public static final int MAX_TYPES = 32;
  public static final int MAX_TEXT = 2048;
  private static final String KEY = "create_bioships:hunting_prey";

  private HuntingCardData() {}

  public static List<ResourceLocation> parse(String text) {
    if (text == null || text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("Prey list is too long.");
    }
    var types = new LinkedHashSet<ResourceLocation>();
    for (String word : text.strip().split("[,\\s]+")) {
      if (word.isBlank()) {
        continue;
      }
      var id = ResourceLocation.tryParse(word);
      if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
        throw new IllegalArgumentException(
            "Unknown entity type: " + word.substring(0, Math.min(word.length(), 128)));
      }
      types.add(id);
      if (types.size() > MAX_TYPES) {
        throw new IllegalArgumentException("Choose at most " + MAX_TYPES + " prey types.");
      }
    }
    return List.copyOf(types);
  }

  public static List<ResourceLocation> read(ItemStack card) {
    if (!card.is(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get())) {
      return List.of();
    }
    var list =
        card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getList(KEY, Tag.TAG_STRING);
    if (list.size() > MAX_TYPES) {
      return List.of();
    }
    try {
      return parse(String.join(",", list.stream().map(Tag::getAsString).toList()));
    } catch (IllegalArgumentException invalid) {
      return List.of();
    }
  }

  public static void write(ItemStack card, String text) {
    if (!card.is(ComponentRegistry.HUNTING_BEHAVIOR_CARD.get()) || card.getCount() != 1) {
      throw new IllegalArgumentException("This is not a hunting card.");
    }
    var types = parse(text);
    var data = card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    var list = new ListTag();
    types.forEach(id -> list.add(StringTag.valueOf(id.toString())));
    data.put(KEY, list);
    card.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
  }
}
