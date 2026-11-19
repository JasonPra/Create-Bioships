package org.Azkerash.create_bioships.compat.letitwalk;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.core.SubLevelHealth;

/** Cached real collision geometry, not empty corners of a body's broad-phase bounds. */
public final class LimbGroundContacts {
  private static final Map<ServerSubLevel, List<AABB>> SHAPES = new WeakHashMap<>();

  private LimbGroundContacts() {}

  public static void invalidate(ServerSubLevel body) {
    SHAPES.remove(body);
  }

  public static List<AABB> shapes(ServerSubLevel physical) {
    return SHAPES.computeIfAbsent(
        physical,
        b -> {
          var result = new ArrayList<AABB>();
          for (var cell : SubLevelHealth.blocks(b))
            for (var box :
                b.getLevel().getBlockState(cell).getCollisionShape(b.getLevel(), cell).toAabbs())
              result.add(box.move(cell));
          return List.copyOf(result);
        });
  }

  public static boolean touching(ServerSubLevel physical, ServerSubLevel observer) {
    if (physical.isRemoved()) return false;
    var shapes = shapes(physical);
    var down =
        physical.logicalPose().orientation().transformInverse(new org.joml.Vector3d(0, -1, 0));
    Vec3 lowest = null;
    double best = -Double.MAX_VALUE;
    for (var box : shapes) {
      var p =
          new Vec3(
              down.x < 0 ? box.minX : box.maxX,
              down.y < 0 ? box.minY : box.maxY,
              down.z < 0 ? box.minZ : box.maxZ);
      double score = p.x * down.x + p.y * down.y + p.z * down.z;
      if (score > best) {
        best = score;
        lowest = p;
      }
    }
    if (lowest == null) return false;
    var point = physical.logicalPose().transformPosition(lowest);
    var ground = TerrainProbe.down(physical.getLevel(), point.add(0, .15, 0), .35, observer);
    return ground != null
        && TerrainProbe.standable(ground)
        && Math.abs(point.y - ground.point().y) < .15;
  }
}
