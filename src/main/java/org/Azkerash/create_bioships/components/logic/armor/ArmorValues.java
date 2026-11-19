package org.Azkerash.create_bioships.components.logic.armor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Shared definitions for armored components, not a second component registry. */
public final class ArmorValues {
  private ArmorValues() {}

  public static double points(BlockState state) {
    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (!id.getNamespace().equals("create_bioships")) return 0;
    return switch (id.getPath()) {
      case "chitin_armor" -> 20;
      case "crab_leg", "limb_joint", "limb_socket", "limb_swivel", "limb_segment", "limb_foot" ->
          10;
      default -> 0;
    };
  }
}
