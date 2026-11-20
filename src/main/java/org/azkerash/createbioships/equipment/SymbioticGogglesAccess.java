package org.azkerash.createbioships.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.azkerash.createbioships.CreateBioships;

/** This mod's inspection whitelist; other mods' Create overlays keep their own access rules. */
public final class SymbioticGogglesAccess {
  private SymbioticGogglesAccess() {}

  public static boolean wearing(Player player) {
    return player != null
        && player.getItemBySlot(EquipmentSlot.HEAD).is(CreateBioships.BIOSHIP_GOGGLES.get());
  }

  public static boolean canInspect(Player player, BlockEntity display) {
    return display == null
        || !BuiltInRegistries.BLOCK
            .getKey(display.getBlockState().getBlock())
            .getNamespace()
            .equals(CreateBioships.MODID)
        || wearing(player);
  }
}
