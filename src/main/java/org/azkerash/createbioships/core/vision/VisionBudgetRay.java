package org.azkerash.createbioships.core.vision;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.*;

/** One straight, voxel-traversed sight query across world terrain and posed Sable geometry. */
final class VisionBudgetRay {
  private static final double EPSILON = 1e-7;

  private record Event(double distance, int cost, int change) {}

  private final ServerLevel level;
  private final Vec3 from, direction;
  private final double budget, loadedLimit, requested;
  private final boolean aquatic;
  private final List<Event> events = new ArrayList<>();
  private double limit;
  private boolean blocked;
  private UUID hitBody;

  private VisionBudgetRay(
      ServerLevel level,
      Vec3 from,
      Vec3 direction,
      double distance,
      double budget,
      boolean aquatic) {
    this.level = level;
    this.from = from;
    this.direction = direction.normalize();
    this.budget = Math.max(0, budget);
    this.aquatic = aquatic;
    requested = Math.max(0, Math.min(distance, budget));
    loadedLimit = VisionRaycaster.loadedDistance(level, from, this.direction, requested);
    limit = loadedLimit;
  }

  static VisionRaycaster.Ray cast(
      ServerLevel level,
      Vec3 from,
      Vec3 direction,
      double distance,
      double budget,
      boolean aquatic,
      ServerSubLevel source) {
    var ray = new VisionBudgetRay(level, from, direction, distance, budget, aquatic);
    if (ray.limit <= EPSILON || direction.lengthSqr() < EPSILON * EPSILON) {
      return new VisionRaycaster.Ray(from, from, false, ray.loadedLimit < ray.requested);
    }
    // Reject self-occlusion before traversing distant world terrain.
    if (source != null && !source.isRemoved()) {
      ray.collectBody(source);
    }
    if (ray.limit > EPSILON) {
      ray.collect(from, from.add(ray.direction.scale(ray.limit)), 0, 1, point -> point, null);
    }
    var container = SubLevelContainer.getContainer(level);
    if (container != null) {
      var query = new AABB(from, from.add(ray.direction.scale(ray.limit))).inflate(EPSILON);
      for (var entry : container.queryIntersecting(new BoundingBox3d(query))) {
        if (!(entry instanceof ServerSubLevel body) || body.isRemoved()) {
          continue;
        }
        if (source == null || !source.getUniqueId().equals(body.getUniqueId())) {
          ray.collectBody(body);
        }
      }
    }
    return ray.finish();
  }

  private void collectBody(ServerSubLevel body) {
    var pose = body.logicalPose();
    var localStart = pose.transformPositionInverse(from);
    var localEnd = pose.transformPositionInverse(from.add(direction.scale(limit)));
    var bounds = body.getPlot().getBoundingBox();
    var span =
        interval(
            localStart,
            localEnd,
            new AABB(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX() + 1d,
                bounds.maxY() + 1d,
                bounds.maxZ() + 1d));
    if (span != null) {
      collect(localStart, localEnd, span[0], span[1], pose::transformPosition, body.getUniqueId());
    }
  }

  private void collect(
      Vec3 start, Vec3 end, double enter, double exit, UnaryOperator<Vec3> toWorld, UUID body) {
    double worldLength = toWorld.apply(end).distanceTo(from);
    BlockGetter.traverseBlocks(
        start.lerp(end, enter),
        start.lerp(end, exit),
        this,
        (trace, cell) -> {
          var span = interval(start, end, new AABB(cell));
          if (span == null) {
            return null;
          }
          double a = span[0] * worldLength, b = Math.min(span[1] * worldLength, limit);
          if (b <= a + EPSILON) {
            return null;
          }
          var state = level.getBlockState(cell);
          int cost = (int) VisionMaterials.cost(state, aquatic);
          if (cost > VisionMaterials.STANDARD_COST) {
            events.add(new Event(a, cost, 1));
            events.add(new Event(b, cost, -1));
          }
          if (!VisionMaterials.glass(state)) {
            var shape = state.getCollisionShape(level, cell);
            var hit = shape.isEmpty() ? null : shape.clip(start, end, cell);
            if (hit != null) {
              double distance =
                  hit.isInside()
                      ? 0
                      : Math.max(0, toWorld.apply(hit.getLocation()).subtract(from).dot(direction));
              if (distance <= limit + EPSILON) {
                limit = Math.min(limit, distance);
                blocked = true;
                hitBody = body;
              }
              return Boolean.TRUE;
            }
          }
          return null;
        },
        trace -> Boolean.FALSE);
  }

  private VisionRaycaster.Ray finish() {
    events.sort(Comparator.comparingDouble(Event::distance));
    double at = 0, remaining = budget;
    int water = 0, glass = 0, index = 0;
    while (at < limit - EPSILON) {
      double next = index < events.size() ? Math.min(limit, events.get(index).distance()) : limit;
      double cost =
          water > 0
              ? VisionMaterials.LAND_WATER_COST
              : glass > 0 ? VisionMaterials.COLORED_GLASS_COST : VisionMaterials.STANDARD_COST;
      double spent = (next - at) * cost;
      if (spent > remaining + EPSILON) {
        return new VisionRaycaster.Ray(
            from, from.add(direction.scale(at + remaining / cost)), false, false);
      }
      remaining -= spent;
      at = next;
      while (index < events.size() && events.get(index).distance() <= at + EPSILON) {
        var event = events.get(index++);
        if (event.cost() == VisionMaterials.LAND_WATER_COST) {
          water += event.change();
        } else {
          glass += event.change();
        }
      }
      if (index >= events.size() && at >= limit - EPSILON) {
        break;
      }
    }
    return new VisionRaycaster.Ray(
        from,
        from.add(direction.scale(limit)),
        blocked,
        !blocked && loadedLimit < requested - EPSILON,
        hitBody);
  }

  /**
   * Parametric entry/exit in the original ray, preserving physical lengths under plot transforms.
   */
  private static double[] interval(Vec3 start, Vec3 end, AABB box) {
    var delta = end.subtract(start);
    double lo = 0, hi = 1;
    for (int axis = 0; axis < 3; axis++) {
      double origin = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
      double velocity = axis == 0 ? delta.x : axis == 1 ? delta.y : delta.z;
      double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
      double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
      if (Math.abs(velocity) < EPSILON) {
        if (origin < min || origin > max) {
          return null;
        }
      } else {
        double a = (min - origin) / velocity, b = (max - origin) / velocity;
        lo = Math.max(lo, Math.min(a, b));
        hi = Math.min(hi, Math.max(a, b));
        if (hi < lo) {
          return null;
        }
      }
    }
    return new double[] {lo, hi};
  }
}
