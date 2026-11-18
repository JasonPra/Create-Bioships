package org.Azkerash.create_bioships.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.Azkerash.create_bioships.Create_bioships;

/** This mod's inspection whitelist; other mods' Create overlays keep their own access rules. */
public final class SymbioticGogglesAccess {
  private SymbioticGogglesAccess() {}

  public static boolean wearing(Player player) {
    return player != null
        && player.getItemBySlot(EquipmentSlot.HEAD).is(Create_bioships.BIOSHIP_GOGGLES.get());
  }

  public static boolean canInspect(Player player, BlockEntity display) {
    return display == null
        || !BuiltInRegistries.BLOCK
            .getKey(display.getBlockState().getBlock())
            .getNamespace()
            .equals(Create_bioships.MODID)
        || wearing(player);
  }
}
