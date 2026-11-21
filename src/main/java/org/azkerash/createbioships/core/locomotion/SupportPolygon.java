package org.azkerash.createbioships.core.locomotion;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Ground support projection, adapted from the old BioshipSupport balance check. */
public final class SupportPolygon {
  private static final double EDGE_TOLERANCE = .02;

  private SupportPolygon() {}

  private static double cross(Vec3 a, Vec3 b, Vec3 c) {
    return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
  }

  public static boolean containsProjection(Vec3 point, List<Vec3> contacts) {
    var sorted = new ArrayList<>(contacts.stream().distinct().toList());
    sorted.sort(
        Comparator.comparingDouble((Vec3 value) -> value.x).thenComparingDouble(value -> value.z));
    if (sorted.size() < 3) {
      return false;
    }
    // A convex hull also handles redundant/interior toes. Contact iteration order
    // and uneven terrain heights cannot change the horizontal support boundary.
    var hull = new ArrayList<Vec3>();
    for (var contact : sorted) {
      while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.getLast(), contact) <= 0) {
        hull.removeLast();
      }
      hull.add(contact);
    }
    int lower = hull.size();
    for (int index = sorted.size() - 2; index >= 0; index--) {
      var contact = sorted.get(index);
      while (hull.size() > lower
          && cross(hull.get(hull.size() - 2), hull.getLast(), contact) <= 0) {
        hull.removeLast();
      }
      hull.add(contact);
    }
    hull.removeLast();
    if (hull.size() < 3) {
      return false;
    }
    for (int index = 0; index < hull.size(); index++) {
      if (cross(hull.get(index), hull.get((index + 1) % hull.size()), point) < -EDGE_TOLERANCE) {
        return false;
      }
    }
    return true;
  }
}
