package org.Azkerash.create_bioships;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import org.Azkerash.create_bioships.disabled.wip.TentacleBlockEntity;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

/** Selects a nearby pursuer for a swipe; never changes the retreat movement decision. */
final class RetreatCombat {
  private RetreatCombat() {}

  static LivingEntity select(LivingCoreBlockEntity core, LivingEntity current) {
    if (eligible(core, current)) return current;
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    return core
        .getLevel()
        .getEntitiesOfClass(
            LivingEntity.class, new AABB(here, here).inflate(6), entity -> eligible(core, entity))
        .stream()
        .min(Comparator.comparingDouble(e -> e.distanceToSqr(here)))
        .orElse(null);
  }

  private static boolean eligible(LivingCoreBlockEntity core, LivingEntity entity) {
    if (entity == null
        || !entity.isAlive()
        || !TentacleBlockEntity.attackable(entity)
        || core.access().allows(core, entity)
        || !BioshipDefense.threatening(core, entity)
        || entity instanceof Creeper && core.autonomy().creeperPanic()) return false;
    var point = entity.getBoundingBox().getCenter();
    double reach = BioshipGraspers.attackReach(core, point);
    return BioshipReach.anchor(core, point).distanceToSqr(point) <= reach * reach
        && BioshipReach.clear(core, point, false, reach);
  }
}
