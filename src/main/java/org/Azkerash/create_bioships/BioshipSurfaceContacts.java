package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/**
 * Contact sensing depends on leg capabilities, not route-search implementation or grasper count.
 */
final class BioshipSurfaceContacts {
  static GroundRoute.Grip grip(LivingCoreBlockEntity core, Vec3 point) {
    if (!core.hatchling()) return null;
    var profile = LocomotionProfiles.forCreature(core);
    var level = (ServerLevel) core.getLevel();
    var anchor =
        point.add(
            core.ship()
                .logicalPose()
                .transformNormal(
                    Vec3.atLowerCornerOf(core.structuralOrigin().subtract(core.getBlockPos()))));
    var faces = new ArrayList<Direction>();
    if (profile.walls()) Direction.Plane.HORIZONTAL.forEach(faces::add);
    if (profile.ceilings()) faces.add(Direction.UP);
    GroundRoute.Grip best = null;
    for (var face : faces) {
      double reach = profile.gripReach() + core.crawl().legTier() * .25;
      var normal = Vec3.atLowerCornerOf(face.getNormal());
      var right =
          face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(-normal.z, 0, normal.x);
      var other = normal.cross(right);
      int contacts = 0;
      double nearest = reach;
      for (int i = 0; i < 4; i++) {
        var from =
            anchor
                .add(right.scale((i % 2 == 0 ? -1 : 1) * .3))
                .add(other.scale((i < 2 ? -1 : 1) * .3));
        var end = from.add(normal.scale(reach));
        var cell = BlockPos.containing(end);
        if (!level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4)) continue;
        var hit = terrainRay(level, from, end);
        if (hit == null
            || hit.getType() != HitResult.Type.BLOCK
            || hit.getDirection() != face.getOpposite()
            || !level.getFluidState(hit.getBlockPos()).isEmpty()) continue;
        double distance = from.distanceTo(hit.getLocation());
        if (distance > .35 && distance <= reach) {
          contacts++;
          nearest = Math.min(nearest, distance);
        }
      }
      if (contacts >= 3 && (best == null || nearest < best.distance()))
        best = new GroundRoute.Grip(face, nearest);
    }
    return best;
  }

  static BlockHitResult terrainRay(ServerLevel level, Vec3 from, Vec3 to) {
    int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .15));
    BlockPos previous = null;
    for (int i = 1; i <= samples; i++) {
      var cell = BlockPos.containing(from.lerp(to, (double) i / samples));
      if (cell.equals(previous)) continue;
      previous = cell;
      if (!level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4)) return null;
      var hit = level.getBlockState(cell).getCollisionShape(level, cell).clip(from, to, cell);
      if (hit != null) return hit;
    }
    return null;
  }
}
