package org.azkerash.createbioships.core.vision;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/** Medium-aware line of sight, plus geometric helpers for a circular 90-degree field of view. */
public final class VisionRaycaster {
  // Geometric cone tessellation only; these directions are never an automatic raycast fan.
  public static final int RAYS_PER_EYE = 13;

  public record Ray(Vec3 from, Vec3 to, boolean blocked, boolean unloaded, java.util.UUID hitBody) {
    public Ray(Vec3 from, Vec3 to, boolean blocked, boolean unloaded) {
      this(from, to, blocked, unloaded, null);
    }
  }

  private VisionRaycaster() {}

  public static List<Vec3> directions(Vec3 forward, Vec3 up) {
    forward = forward.normalize();
    Vec3 right = forward.cross(up).normalize();
    up = right.cross(forward).normalize();
    var rays = new ArrayList<Vec3>();
    rays.add(forward);
    for (int ring = 1; ring <= 2; ring++) {
      int count = ring * 4;
      double spread = Math.tan(Math.toRadians(ring * 22.5));
      for (int i = 0; i < count; i++) {
        double angle = i * Math.PI * 2 / count;
        rays.add(
            forward
                .add(right.scale(Math.cos(angle) * spread))
                .add(up.scale(Math.sin(angle) * spread))
                .normalize());
      }
    }
    return List.copyOf(rays);
  }

  /** Chunk DDA clips at the first unloaded chunk without fetching or generating it. */
  public static double loadedDistance(ServerLevel level, Vec3 start, Vec3 direction, double range) {
    int x = net.minecraft.util.Mth.floor(start.x) >> 4;
    int z = net.minecraft.util.Mth.floor(start.z) >> 4;
    if (!level.hasChunk(x, z)) {
      return 0;
    }
    int sx = direction.x > 0 ? 1 : -1, sz = direction.z > 0 ? 1 : -1;
    double dx = direction.x == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(direction.x);
    double dz = direction.z == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(direction.z);
    double tx =
        direction.x == 0
            ? Double.POSITIVE_INFINITY
            : ((sx > 0 ? (x + 1) * 16.0 : x * 16.0) - start.x) / direction.x;
    double tz =
        direction.z == 0
            ? Double.POSITIVE_INFINITY
            : ((sz > 0 ? (z + 1) * 16.0 : z * 16.0) - start.z) / direction.z;
    while (Math.min(tx, tz) <= range) {
      double next = Math.min(tx, tz);
      if (tx <= next) {
        x += sx;
        tx += dx;
      }
      if (tz <= next) {
        z += sz;
        tz += dz;
      }
      if (!level.hasChunk(x, z)) {
        return Math.max(0, next - .001);
      }
    }
    return range;
  }

  public static Ray cast(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body, Vec3 from, Vec3 direction, double range) {
    return cast(body, from, direction, range, range, false);
  }

  public static Ray cast(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body,
      Vec3 from,
      Vec3 direction,
      double distance,
      double budget,
      boolean aquatic) {
    return VisionBudgetRay.cast(body.getLevel(), from, direction, distance, budget, aquatic, body);
  }

  public static Ray cast(ServerLevel level, Vec3 from, Vec3 direction, double range) {
    return VisionBudgetRay.cast(level, from, direction, range, range, false, null);
  }
}
