package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

/** Executes selected item intake through an available mouth-bound grasper; never selects a goal. */
final class BioshipFoodCollection {
  static Vec3 collect(LivingCoreBlockEntity core, Vec3 point, ItemEntity item) {
    var stomach = DigestiveSystem.stomach(core);
    if (stomach == null) return null;
    if (item == null) return point;
    if (!item.isAlive()) return null;
    var grasper = BioshipGraspers.available(core, point, core.assimilation().busyGraspers());
    if (grasper == null) return point;
    var intake =
        grasper.key().equals("core")
            ? core.mouthPosition(core.ship())
            : core.ship().logicalPose().transformPosition(grasper.root());
    if (!BioshipReach.clear(core, point, false)) return point;
    core.crawl().reach(item.position());
    item.setDeltaMovement(
        intake
            .subtract(item.position())
            .normalize()
            .scale(grasper.swimming() && core.aquatic().wet() ? .45 : .3));
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    if (here.distanceToSqr(point) <= 4
        || core.mouthPosition(core.ship()).distanceToSqr(point) <= 2.25
        || BioshipReach.anchor(core, point).distanceToSqr(point) <= 2.25) {
      var one = item.getItem().copyWithCount(1);
      boolean accepted;
      if (BioshipScavenging.droppedBlock(one))
        accepted = core.scavenging().consumeDroppedBlock(core, one);
      else if ((one.is(BioshipIntegration.constructionMaterial())
              || BioshipNutrition.fuelValue(one) > 0)
          && !LivingCoreBlockEntity.isMeat(one)) {
        stomach.retain(one);
        accepted = true;
      } else
        accepted =
            grasper.key().equals("core")
                ? DigestiveSystem.offerFood(core, one, false)
                : DigestiveSystem.offerFood(core, grasper.mouth(), one, false);
      if (accepted) {
        if (core.getLevel().getBlockEntity(grasper.mouth())
            instanceof FeedingMouthBlockEntity mouth) mouth.animate(10, false);
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) item.discard();
      }
    }
    return null;
  }
}
