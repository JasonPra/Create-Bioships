package org.azkerash.createbioships.core.legs;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeGait;
import org.azkerash.createbioships.core.vision.VisionRaycaster;
import org.joml.Vector3d;

/**
 * Single bounded collision-shape trace, including foreign moving Sable surfaces. No chunk loads.
 */
public final class SurfaceContactProbe {
  private static final double SURFACE_EPSILON = .001;
  private static final double MAX_PROBE_LENGTH = 16;

  private SurfaceContactProbe() {}

  public static PrototypeGait.Contact cast(ServerSubLevel owner, Vec3 from, Vec3 to) {
    double length = from.distanceTo(to);
    if (!(length > SURFACE_EPSILON && length <= MAX_PROBE_LENGTH)) {
      return null;
    }
    var direction = to.subtract(from).normalize();
    double loaded = VisionRaycaster.loadedDistance(owner.getLevel(), from, direction, length);
    if (loaded <= SURFACE_EPSILON) {
      return null;
    }
    var end = from.add(direction.scale(Math.min(length, loaded)));
    var best = trace(owner, null, from, end);
    var container = SubLevelContainer.getContainer(owner.getLevel());
    if (container != null) {
      var query = new AABB(from, end).inflate(SURFACE_EPSILON);
      for (var raw : container.queryIntersecting(new BoundingBox3d(query))) {
        if (!(raw instanceof ServerSubLevel surface)
            || surface.isRemoved()
            || SubLevelCreatureParts.root(surface) == owner) {
          continue;
        }
        var hit = trace(owner, surface, from, end);
        if (hit != null
            && (best == null
                || hit.point().distanceToSqr(from) < best.point().distanceToSqr(from))) {
          best = hit;
        }
      }
    }
    return best;
  }

  private static PrototypeGait.Contact trace(
      ServerSubLevel owner, ServerSubLevel surface, Vec3 from, Vec3 to) {
    var pose = surface == null ? null : surface.logicalPose();
    var start = pose == null ? from : pose.transformPositionInverse(from);
    var end = pose == null ? to : pose.transformPositionInverse(to);
    UnaryOperator<Vec3> world = pose == null ? point -> point : pose::transformPosition;
    return BlockGetter.traverseBlocks(
        start,
        end,
        owner,
        (body, cell) -> {
          if (surface == null && !body.getLevel().hasChunkAt(cell)) {
            return null;
          }
          var state = body.getLevel().getBlockState(cell);
          // Fluid never anchors a toe; waterlogged solid shapes still can.
          var shape = state.getCollisionShape(body.getLevel(), cell);
          var hit = shape.isEmpty() ? null : shape.clip(start, end, cell);
          if (hit == null || hit.isInside()) {
            return null;
          }
          var localNormal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
          var normal = localNormal;
          if (pose != null) {
            var rotated = pose.orientation().transform(new Vector3d(normal.x, normal.y, normal.z));
            normal = new Vec3(rotated.x, rotated.y, rotated.z).normalize();
          }
          if (normal.dot(to.subtract(from)) >= 0) {
            return null;
          }
          var point = world.apply(hit.getLocation());
          return new PrototypeGait.Contact(
              point,
              surface == null ? null : surface.getUniqueId(),
              hit.getLocation(),
              normal,
              localNormal);
        },
        body -> null);
  }
}
