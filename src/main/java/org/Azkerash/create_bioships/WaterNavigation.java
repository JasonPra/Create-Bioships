package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.navigation.WaterRoutePlanner;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.Azkerash.create_bioships.network.VisionDebug;

/** Per-creature waypoints/retries. Respiration chooses the goal; propulsion follows the intent. */
final class WaterNavigation {
  private List<Vec3> path = List.of();
  private Vec3 destination;
  private int cursor, expanded;
  private long plannedAt = Long.MIN_VALUE, retryAfter;
  private String status = "idle";
  private boolean airRoute;

  void clear() {
    path = List.of();
    destination = null;
    cursor = 0;
    retryAfter = 0;
    status = "idle";
    airRoute = false;
  }

  List<Vec3> remaining() {
    return List.copyOf(path.subList(Math.min(cursor, path.size()), path.size()));
  }

  Vec3 toward(LivingCoreBlockEntity core, Vec3 target) {
    return steer(core, target, false);
  }

  Vec3 towardAir(LivingCoreBlockEntity core) {
    return steer(core, null, true);
  }

  private Vec3 steer(LivingCoreBlockEntity core, Vec3 target, boolean air) {
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    long now = core.getLevel().getGameTime();
    var space = new WaterNavigationSpace(core, air);
    boolean changed =
        air != airRoute
            || !air && (destination == null || destination.distanceToSqr(target) > 2.25);
    while (cursor < path.size() && here.distanceToSqr(path.get(cursor)) < .16) {
      if (cursor + 1 < path.size() && !nextClear(space, here, path.get(cursor + 1))) break;
      cursor++;
    }
    boolean blocked = cursor < path.size() && !nextClear(space, here, path.get(cursor));
    if (changed || now >= retryAfter && (blocked || path.isEmpty() || now - plannedAt >= 40)) {
      var result =
          air
              ? airRoute(core, here, space)
              : WaterRoutePlanner.toTarget(
                  WaterNavigationSpace.point(here),
                  WaterNavigationSpace.point(target),
                  1.1,
                  space,
                  4096,
                  48);
      path = result.points().stream().map(WaterNavigationSpace::vector).toList();
      cursor = path.size() > 1 ? 1 : path.size();
      destination = air ? (path.isEmpty() ? null : path.getLast()) : target;
      airRoute = air;
      plannedAt = now;
      retryAfter = now + 20;
      status = result.status().name().toLowerCase(Locale.ROOT);
      expanded = result.expanded();
      blocked = cursor < path.size() && !nextClear(space, here, path.get(cursor));
    }
    if (blocked || cursor >= path.size()) return Vec3.ZERO;
    var delta = path.get(cursor).subtract(here);
    return delta.normalize().scale(Math.min(1, delta.length() * 2));
  }

  private WaterRoutePlanner.Result airRoute(
      LivingCoreBlockEntity core, Vec3 here, WaterNavigationSpace space) {
    var start = WaterNavigationSpace.point(here);
    double preferredY = here.y + 8;
    for (int height = 0; height <= 64; height++) {
      var point = here.add(0, height, 0);
      var candidate = WaterNavigationSpace.point(point);
      if (!BioshipRespiration.airAccessAt(core, point) || !space.fits(candidate)) continue;
      preferredY = point.y;
      if (space.clear(start, candidate))
        return new WaterRoutePlanner.Result(
            List.of(start, candidate), WaterRoutePlanner.Status.COMPLETE, 0);
      break;
    }
    final double airHeight = preferredY;
    return WaterRoutePlanner.find(
        start,
        p -> BioshipRespiration.airAccessAt(core, WaterNavigationSpace.vector(p)),
        p -> Math.max(0, airHeight - p.y()),
        space,
        4096,
        64);
  }

  private boolean nextClear(WaterNavigationSpace space, Vec3 from, Vec3 to) {
    double distance = from.distanceTo(to);
    return space.clear(
        WaterNavigationSpace.point(from),
        WaterNavigationSpace.point(distance > 1 ? from.lerp(to, 1 / distance) : to));
  }

  CompoundTag debug(long now) {
    var tag = new CompoundTag();
    tag.putString("Status", "water_" + status);
    tag.putString("Mode", airRoute ? "surface" : "swim");
    tag.putInt("Expanded", expanded);
    tag.putLong("Age", plannedAt == Long.MIN_VALUE ? 0 : Math.max(0, now - plannedAt));
    tag.putLong("RetryIn", Math.max(0, retryAfter - now));
    if (destination != null) tag.put("Goal", VisionDebug.vector(destination));
    return tag;
  }
}
