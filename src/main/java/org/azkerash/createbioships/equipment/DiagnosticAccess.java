package org.azkerash.createbioships.equipment;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.player.Player;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.core.access.SubLevelPermissions;
import org.azkerash.createbioships.development.testing.DevelopmentToolkitItem;

/** Shared item eligibility and per-creature disclosure policy for both diagnostic entry points. */
public final class DiagnosticAccess {
  private DiagnosticAccess() {}

  public static boolean allowed(Player player) {
    return player != null
        && (DevelopmentToolkitItem.allowed(player)
            || player
                .getInventory()
                .contains(CreateBioships.SOMATIC_RESONATOR.get().getDefaultInstance()));
  }

  public static boolean canInspect(ServerSubLevel body, Player player) {
    return allowed(player) && SubLevelPermissions.canInspect(body, player.getUUID());
  }
}
