package org.azkerash.createbioships.core.pathfinding;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.*;

/**
 * Transient, grid-owned active routes. Behaviors publish their real route, never a debug replan.
 */
public final class SubLevelPaths {
  private static final int ROUTE_EXPIRY_TICKS = 40;

  public static final int MAX_POINTS = 256;

  public record Snapshot(
      SubLevelPathfinder.Mode mode, List<Vec3> points, int next, long tick, long revision) {}

  private static final Map<ServerSubLevel, Snapshot> ACTIVE = new WeakHashMap<>();

  private SubLevelPaths() {}

  public static void update(
      ServerSubLevel body, SubLevelPathfinder.Mode mode, List<Vec3> route, int next) {
    body = SubLevelCreatureParts.root(body);
    if (route.isEmpty() || next < 0 || next >= route.size() || route.size() > MAX_POINTS) {
      clear(body);
      return;
    }
    ACTIVE.put(
        body,
        new Snapshot(
            mode,
            List.copyOf(route),
            next,
            body.getLevel().getGameTime(),
            org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.motionRevision(body)));
  }

  public static void clear(ServerSubLevel body) {
    ACTIVE.remove(SubLevelCreatureParts.root(body));
  }

  public static Snapshot get(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    var route = ACTIVE.get(body);
    if (route == null) {
      return null;
    }
    if (body.isRemoved()
        || SubLevelHealth.status(body).dead()
        || body.getLevel().getGameTime() - route.tick() > ROUTE_EXPIRY_TICKS
        || route.revision()
            != org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.motionRevision(
                body)) {
      ACTIVE.remove(body);
      return null;
    }
    return route;
  }
}
