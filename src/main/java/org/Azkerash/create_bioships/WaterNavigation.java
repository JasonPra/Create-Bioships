package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.navigation.WaterRoutePlanner;
import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;
import org.Azkerash.create_bioships.network.VisionDebug;

/** Per-creature waypoints and retry policy. Search and physical propulsion have separate owners. */
final class WaterNavigation {
  private List<Vec3> path = List.of();
  private Vec3 destination;
  private int cursor, expanded;
  private long plannedAt = Long.MIN_VALUE, retryAfter;
  private String status = "idle";

  void clear() {
    path = List.of();
    destination = null;
    cursor = 0;
    retryAfter = 0;
    status = "idle";
  }

  List<Vec3> remaining() {
    return List.copyOf(path.subList(Math.min(cursor, path.size()), path.size()));
  }

  Vec3 toward(LivingCoreBlockEntity core, Vec3 target) {
    var here = BodyCoordinates.physical(core.amoebaComponents(), net.minecraft.core.BlockPos.ZERO);
    long now = core.getLevel().getGameTime();
    var space = new WaterNavigationSpace(core, false);
    boolean changed = destination == null || destination.distanceToSqr(target) > 2.25;
    while (cursor < path.size() && here.distanceToSqr(path.get(cursor)) < .16) cursor++;
    boolean blocked = cursor < path.size() && !nextClear(space, here, path.get(cursor));
    if (changed || now >= retryAfter && (blocked || path.isEmpty() || now - plannedAt >= 40)) {
      var result =
          WaterRoutePlanner.toTarget(
              WaterNavigationSpace.point(here),
              WaterNavigationSpace.point(target),
              1.1,
              space,
              4096,
              48);
      path = result.points().stream().map(WaterNavigationSpace::vector).toList();
      cursor = path.size() > 1 ? 1 : path.size();
      destination = target;
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

  private boolean nextClear(WaterNavigationSpace space, Vec3 from, Vec3 to) {
    double distance = from.distanceTo(to);
    return space.clear(
        WaterNavigationSpace.point(from),
        WaterNavigationSpace.point(distance > 1 ? from.lerp(to, 1 / distance) : to));
  }

  CompoundTag debug(long now) {
    var tag = new CompoundTag();
    tag.putString("Status", "water_" + status);
    tag.putString("Mode", "swim");
    tag.putInt("Expanded", expanded);
    tag.putLong("Age", plannedAt == Long.MIN_VALUE ? 0 : Math.max(0, now - plannedAt));
    tag.putLong("RetryIn", Math.max(0, retryAfter - now));
    if (destination != null) tag.put("Goal", VisionDebug.vector(destination));
    return tag;
  }
}
