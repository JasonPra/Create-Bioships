package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.azkerash.createbioships.CreateBioships;

/** All loaded Biomancy/own IDs, plus data-pack IDs or block groups explicitly whitelisted. */
public final class BlockPlacementRules {
  public static final TagKey<Block> EXTRA_ALLOWED =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(CreateBioships.MODID, "creature_placeable"));
  private static Set<ResourceLocation> allowed = Set.of();

  private BlockPlacementRules() {}

  public static void refresh() {
    var ids = new TreeSet<ResourceLocation>();
    for (var id : BuiltInRegistries.BLOCK.keySet()) {
      if (id.getNamespace().equals("biomancy") || id.getNamespace().equals(CreateBioships.MODID)) {
        ids.add(id);
      }
    }
    allowed = Collections.unmodifiableSet(ids);
  }

  public static Set<ResourceLocation> allowedIds() {
    return allowed;
  }

  public static boolean allows(BlockState state) {
    return allowed.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
        || state.is(EXTRA_ALLOWED);
  }
}
