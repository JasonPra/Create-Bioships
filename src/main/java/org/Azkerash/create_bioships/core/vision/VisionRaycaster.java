package org.Azkerash.create_bioships.core.vision;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.Azkerash.create_bioships.core.CoreAssemblyRules;

/** Circular 90-degree cone: center, four inner rays and eight rim rays. */
public final class VisionRaycaster {
  public static final int RAYS_PER_EYE = 13;

  public record Ray(Vec3 from, Vec3 to, boolean blocked, boolean unloaded) {}

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
    if (!level.hasChunk(x, z)) return 0;
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
      if (!level.hasChunk(x, z)) return Math.max(0, next - .001);
    }
    return range;
  }

  public static Ray cast(
      dev.ryanhcode.sable.sublevel.ServerSubLevel body, Vec3 from, Vec3 direction, double range) {
    var level = body.getLevel();
    var world = cast(level, from, direction, range);
    var pose = body.logicalPose();
    var start = pose.transformPositionInverse(from);
    var end = pose.transformPositionInverse(world.to());
    var context =
        new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            CollisionContext.empty());
    ((dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension) context)
        .sable$setDoNotProject(true);
    var own = level.clip(context);
    if (own.getType() == HitResult.Type.BLOCK) {
      var point = pose.transformPosition(own.getLocation());
      if (point.distanceToSqr(from) <= world.to().distanceToSqr(from))
        return new Ray(from, point, true, false);
    }
    return world;
  }

  public static Ray cast(ServerLevel level, Vec3 from, Vec3 direction, double range) {
    double distance = loadedDistance(level, from, direction, range);
    Vec3 end = from.add(direction.scale(distance));
    if (distance == 0) return new Ray(from, from, false, true);
    var hit =
        level.clip(
            new ClipContext(
                from,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
    boolean blocked = hit.getType() == HitResult.Type.BLOCK;
    if (blocked) {
      // Native Sable clips return logical plot coordinates for a grid hit.
      var struck = CoreAssemblyRules.containing(level, hit.getBlockPos());
      end =
          struck == null
              ? hit.getLocation()
              : struck.logicalPose().transformPosition(hit.getLocation());
    }
    return new Ray(from, end, blocked, !blocked && distance < range);
  }
}
