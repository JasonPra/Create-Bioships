package org.azkerash.createbioships.core.locomotion.prototype;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** A bounded emergency step search, independent of body size and combat collision definitions. */
public final class FootholdRecovery {
  private static final double ENDPOINT_TOLERANCE = .15, MIN_RISE = .1, GRID_SPACING = .75;
  private static final int SEARCH_RADIUS = 2, MAX_CANDIDATES = 9;
  private static final double MIN_SUPPORT_UP = .7;

  private FootholdRecovery() {}

  public static PrototypeGait.Contact nearest(
      PrototypeGait.Leg leg, Vec3 foot, Vec3 obstacle, PrototypeGait.Terrain terrain) {
    var up = terrain.up(leg).normalize();
    var side = leg.outward().subtract(up.scale(leg.outward().dot(up))).normalize();
    if (side.lengthSqr() < .1) {
      return null;
    }
    var across = up.cross(side).normalize();
    var candidates = new ArrayList<Vec3>();
    for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
      for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
        candidates.add(
            obstacle.add(side.scale(x * GRID_SPACING)).add(across.scale(z * GRID_SPACING)));
      }
    }
    candidates.sort(Comparator.comparingDouble(p -> p.distanceToSqr(foot)));
    PrototypeGait.Contact best = null;
    double distance = Double.POSITIVE_INFINITY;
    int tested = 0;
    for (var desired : candidates) {
      if (tested++ >= MAX_CANDIDATES) {
        break;
      }
      var contact = terrain.probe(leg, desired, leg.height() + 1, null);
      if (contact == null
          || contact.point().subtract(foot).dot(up) < MIN_RISE
          || terrain.normal(contact).dot(up) < MIN_SUPPORT_UP) {
        continue;
      }
      double candidateDistance = contact.point().distanceToSqr(foot);
      if (candidateDistance >= distance) {
        continue;
      }
      boolean valid = true;
      double lift = Math.max(.5, contact.point().subtract(foot).dot(up) + .25);
      for (double t : new double[] {.25, .5, .75, 1}) {
        var point = foot.lerp(contact.point(), t).add(up.scale(Math.sin(Math.PI * t) * lift));
        var pose = terrain.solveRecovery(leg, point);
        if (pose.getLast().distanceTo(point) > ENDPOINT_TOLERANCE
            || !terrain.clearPose(leg, pose)) {
          valid = false;
          break;
        }
      }
      if (valid) {
        best = contact;
        distance = candidateDistance;
      }
    }
    return best;
  }
}
