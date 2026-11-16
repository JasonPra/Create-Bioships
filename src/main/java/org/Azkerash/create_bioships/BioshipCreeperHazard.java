package org.Azkerash.create_bioships;

import net.minecraft.world.entity.monster.Creeper;
import org.Azkerash.create_bioships.mixin.CreeperBlastAccess;

/** Native explosion power has twice that entity-damage reach; clearance includes a core cell. */
final class BioshipCreeperHazard {
  record Estimate(double damageReach, int fuseRemaining) {
    double clearance() {
      return damageReach + 1;
    }

    double reactionRange() {
      return damageReach + 2;
    }
  }

  static Estimate estimate(Creeper creeper) {
    var nativeState = (CreeperBlastAccess) creeper;
    double power =
        Math.max(0, nativeState.bioships$explosionRadius()) * (creeper.isPowered() ? 2d : 1d);
    boolean primed =
        creeper.isIgnited() || creeper.getSwellDir() > 0 || nativeState.bioships$swell() > 0;
    int remaining =
        primed
            ? Math.max(0, nativeState.bioships$maxSwell() - nativeState.bioships$swell())
            : Integer.MAX_VALUE;
    return new Estimate(power * 2, remaining);
  }

  private BioshipCreeperHazard() {}
}
