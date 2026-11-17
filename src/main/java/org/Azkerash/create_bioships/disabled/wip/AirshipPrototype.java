package org.Azkerash.create_bioships.disabled.wip;

import org.Azkerash.create_bioships.Create_bioships;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/** Preserved prototype assets and APIs are not an implemented gameplay stage. */
public final class AirshipPrototype {
  private AirshipPrototype() {}

  public static boolean available() {
    return false;
  }

  public static boolean component(Block block) {
    var id = BuiltInRegistries.BLOCK.getKey(block);
    if (!id.getNamespace().equals(Create_bioships.MOD_ID)) return false;
    return id.getPath().startsWith("nerve_")
        || switch (id.getPath()) {
          case "living_helm",
              "living_throttle",
              "vertical_trim",
              "blueprint_panel",
              "access_panel",
              "living_perch",
              "buoyancy_organ",
              "living_door" ->
              true;
          default -> false;
        };
  }
}
