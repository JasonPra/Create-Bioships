package org.Azkerash.create_bioships.equipment;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.player.Player;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem;

/** Shared item eligibility and per-creature disclosure policy for both diagnostic entry points. */
public final class DiagnosticAccess {
  private DiagnosticAccess() {}

  public static boolean allowed(Player player) {
    return player != null
        && (DevelopmentToolkitItem.allowed(player)
            || player
                .getInventory()
                .contains(Create_bioships.SOMATIC_RESONATOR.get().getDefaultInstance()));
  }

  public static boolean canInspect(ServerSubLevel body, Player player) {
    return allowed(player) && SubLevelPermissions.canInspect(body, player.getUUID());
  }
}
