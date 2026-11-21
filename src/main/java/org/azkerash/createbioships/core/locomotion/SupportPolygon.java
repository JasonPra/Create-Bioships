package org.azkerash.createbioships.core.locomotion;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Ground support projection, adapted from the old BioshipSupport balance check. */
public final class SupportPolygon {
  private static final double EDGE_TOLERANCE = .05;

  private SupportPolygon() {}

  private static double cross(Vec3 a, Vec3 b, Vec3 c) {
    return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
  }

  /** Project onto the actual support surface, including vertical and inverted adhesion. */
  public static boolean containsProjection(Vec3 point, List<Vec3> contacts, Vec3 normal) {
    if (normal.lengthSqr() < 1e-8) {
      return false;
    }
    var up = normal.normalize();
    var seed = Math.abs(up.x) < .9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
    var right = seed.subtract(up.scale(seed.dot(up))).normalize();
    var forward = right.cross(up).normalize();
    var projected =
        contacts.stream()
            .map(contact -> new Vec3(contact.dot(right), 0, contact.dot(forward)))
            .toList();
    return containsProjection(new Vec3(point.dot(right), 0, point.dot(forward)), projected);
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
      var from = hull.get(index);
      var to = hull.get((index + 1) % hull.size());
      double edgeLength = Math.hypot(to.x - from.x, to.z - from.z);
      // Distances are blocks; scale the cross product by edge length so a
      // finite toe contact patch does not shrink as the creature grows.
      if (cross(from, to, point) < -EDGE_TOLERANCE * edgeLength) {
        return false;
      }
    }
    return true;
  }
}
