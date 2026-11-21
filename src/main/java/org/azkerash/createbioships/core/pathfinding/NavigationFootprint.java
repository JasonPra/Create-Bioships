package org.azkerash.createbioships.core.pathfinding;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.compat.letitwalk.CompactLegCollisions;
import org.azkerash.createbioships.compat.letitwalk.CompactLimbController;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.legs.SubLevelLegs;

/** Snapshot actual hulls and full articulated limb boxes, not the empty space between legs. */
final class NavigationFootprint {
  private static final double CONTACT_TOLERANCE_BLOCKS = .025;
  private final List<AABB> hulls;
  private final CompactLegCollisions.Snapshot legs;

  NavigationFootprint(ServerSubLevel body) {
    var boxes = new ArrayList<AABB>();
    for (var member : SubLevelCreatureParts.members(body)) {
      if (member != body
          && member.getUserDataTag() != null
          && member.getUserDataTag().contains(CompactLimbController.PART)) {
        continue;
      }
      var box = member.boundingBox();
      boxes.add(
          new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
              .deflate(CONTACT_TOLERANCE_BLOCKS));
    }
    hulls = List.copyOf(boxes);
    legs = CompactLegCollisions.snapshot(body, SubLevelLegs.poses(body));
  }

  /** Offset applies to the whole current pose; terrain below the support plane is contact. */
  boolean intersects(AABB obstacle, Vec3 offset, double groundPlane, boolean includeLegs) {
    if (obstacle.maxY <= groundPlane) {
      return false;
    }
    var clipped =
        new AABB(
                obstacle.minX,
                Math.max(obstacle.minY, groundPlane),
                obstacle.minZ,
                obstacle.maxX,
                obstacle.maxY,
                obstacle.maxZ)
            .move(offset.scale(-1));
    for (var hull : hulls) {
      if (hull.intersects(clipped)) {
        return true;
      }
    }
    return includeLegs && legs.overlaps(clipped, CONTACT_TOLERANCE_BLOCKS);
  }
}
