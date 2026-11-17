package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

/** Grounded recovery uses reachable footholds, including a shallow submerged seabed. */
final class BioshipRighting {
  record Support(double height) {}

  static Support support(LivingCoreBlockEntity core) {
    var ship = core.ship();
    var up = ship.logicalPose().transformNormal(new Vec3(0, 1, 0));
    if (up.y > .75) return null;
    var layout = BioshipSupport.layout(core);
    if (layout.legs().size() != 4) return null;
    var origin = Vec3.atLowerCornerOf(core.structuralOrigin());
    var center = ship.logicalPose().transformPosition(origin.add(layout.center()));
    var contacts = new ArrayList<Vec3>();
    double top = 0;
    double reach = core.crawl().footingReach();
    for (var leg : layout.legs()) {
      var root = ship.logicalPose().transformPosition(origin.add(leg.root()));
      Vec3 contact = null;
      for (double radius : new double[] {.8, 1.3, 1.8}) {
        int index = contacts.size();
        var point =
            new Vec3(
                center.x + (index % 2 == 0 ? -radius : radius),
                root.y,
                center.z + (index < 2 ? -radius : radius));
        for (int y = (int) Math.floor(root.y); y >= Math.floor(root.y - reach); y--) {
          var floor = BlockPos.containing(point.x, y, point.z);
          var level = core.getLevel();
          if (!level.getChunkSource().hasChunk(floor.getX() >> 4, floor.getZ() >> 4)) continue;
          if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
              || !level
                  .getBlockState(floor.above())
                  .getCollisionShape(level, floor.above())
                  .isEmpty()) continue;
          var candidate = new Vec3(point.x, y + 1, point.z);
          if (root.distanceToSqr(candidate) <= reach * reach) {
            contact = candidate;
            break;
          }
        }
        if (contact != null) break;
      }
      if (contact != null) {
        contacts.add(contact);
        top += contact.y;
      }
    }
    if (contacts.size() < 3
        || !BioshipSupport.containsProjection(center, contacts.toArray(Vec3[]::new))) return null;
    var corePosition = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    return new Support(top / contacts.size() + .85 + corePosition.y - center.y);
  }

  static Vec3 torque(Vec3 up, Vec3 target, Vec3 right) {
    var axis = up.cross(target);
    if (up.dot(target) < -.9 && axis.lengthSqr() < .01) return right.normalize();
    return axis;
  }
}
