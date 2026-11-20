package org.Azkerash.create_bioships.components.logic.armor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Shared definitions for armored components, not a second component registry. */
public final class ArmorValues {
  private ArmorValues() {}

  /** Direction is in sublevel-local block space; unknown impact faces cannot protect a maw. */
  public static boolean protects(BlockState state, net.minecraft.core.Direction face) {
    if (state.is(org.Azkerash.create_bioships.components.ComponentRegistry.ARMORED_MAW.get())) {
      return face != null
          && face
              == state.getValue(
                  org.Azkerash.create_bioships.components.logic.mouth.MouthBlock.FACING);
    }
    return points(state) > 0;
  }

  public static double points(BlockState state) {
    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (!id.getNamespace().equals("create_bioships")) {
      return 0;
    }
    return switch (id.getPath()) {
      case "chitin_armor", "armored_maw" -> 20;
      case "crab_leg", "limb_joint", "limb_socket", "limb_swivel", "limb_segment", "limb_foot" ->
          10;
      default -> 0;
    };
  }
}
