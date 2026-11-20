package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.components.logic.legs.CrabLegBlock;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.joml.Intersectiond;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Queries the actual native segment poses. Physics and contact resolution belong to Sable. */
public final class CompactLegCollisions {
  private CompactLegCollisions() {}

  private static final double[] WIDTHS = {.23, .17, .09};

  public static double width(int segment, int height) {
    return WIDTHS[segment] * CrabLegKinematics.thickness(height);
  }

  public static boolean grounded(
      ServerSubLevel owner, List<ServerSubLevel> parts, double[] lengths) {
    // The distal foot normally supplies contact. Keep proximal checks for fallen/rotated limbs.
    for (int i = parts.size() - 1; i >= 0; i--) {
      var p = parts.get(i);
      var q = p.logicalPose().orientation();
      var base = Vec3.atLowerCornerOf(p.getPlot().getCenterBlock()).add(.5, 0, .5);
      var point =
          p.logicalPose()
              .transformPosition(
                  base.add(0, q.transform(new Vector3d(0, 1, 0)).y >= 0 ? 0 : lengths[i], 0));
      // Probe the lower envelope, not the planned foot target.
      var state = p.getLevel().getBlockState(p.getPlot().getCenterBlock());
      int height =
          state.hasProperty(
                  org.azkerash.createbioships.components.logic.legs.CompactLimbBlock.HEIGHT)
              ? state.getValue(
                  org.azkerash.createbioships.components.logic.legs.CompactLimbBlock.HEIGHT)
              : 1;
      var x = q.transform(new Vector3d(1, 0, 0));
      var z = q.transform(new Vector3d(0, 0, 1));
      double half = width(i, height) / 2;
      point =
          point.add(
              -Math.signum(x.y) * x.x * half - Math.signum(z.y) * z.x * half,
              -Math.abs(x.y) * half - Math.abs(z.y) * half,
              -Math.signum(x.y) * x.z * half - Math.signum(z.y) * z.z * half);
      if (!owner.getLevel().hasChunkAt(BlockPos.containing(point))) {
        continue;
      }
      var ground = TerrainProbe.down(owner.getLevel(), point.add(0, .15, 0), .35, owner);
      if (ground != null
          && TerrainProbe.standable(ground)
          && Math.abs(point.y - ground.point().y) < .15) {
        return true;
      }
    }
    return false;
  }

  private record Box(Vector3d center, Vector3d extent, Quaterniond rotation) {}

  private static List<Box> boxes(ServerSubLevel body, List<SubLevelLegs.Pose> poses) {
    var result = new ArrayList<Box>();
    for (var pose : poses) {
      var state = body.getLevel().getBlockState(pose.cell());
      if (!state.hasProperty(CrabLegBlock.HEIGHT)) {
        continue;
      }
      int height = state.getValue(CrabLegBlock.HEIGHT);
      for (int i = 0; i < 3; i++) {
        var a = body.logicalPose().transformPosition(pose.collisionEndpoints().get(i * 2));
        var b = body.logicalPose().transformPosition(pose.collisionEndpoints().get(i * 2 + 1));
        var d = b.subtract(a);
        if (d.lengthSqr() < 1e-8) {
          continue;
        }
        var c = a.add(b).scale(.5);
        result.add(
            new Box(
                new Vector3d(c.x, c.y, c.z),
                new Vector3d(width(i, height) / 2, d.length() / 2, width(i, height) / 2),
                new Quaterniond()
                    .rotationTo(new Vector3d(0, 1, 0), new Vector3d(d.x, d.y, d.z).normalize())));
      }
    }
    return result;
  }

  public static AABB bounds(ServerSubLevel body, List<SubLevelLegs.Pose> poses) {
    AABB result = null;
    for (var b : boxes(body, poses)) {
      var q = b.rotation;
      var h = b.extent;
      var x = q.transform(new Vector3d(1, 0, 0));
      var y = q.transform(new Vector3d(0, 1, 0));
      var z = q.transform(new Vector3d(0, 0, 1));
      double ex = Math.abs(x.x) * h.x + Math.abs(y.x) * h.y + Math.abs(z.x) * h.z,
          ey = Math.abs(x.y) * h.x + Math.abs(y.y) * h.y + Math.abs(z.y) * h.z,
          ez = Math.abs(x.z) * h.x + Math.abs(y.z) * h.y + Math.abs(z.z) * h.z;
      var c = b.center;
      var a = new AABB(c.x - ex, c.y - ey, c.z - ez, c.x + ex, c.y + ey, c.z + ez);
      result = result == null ? a : result.minmax(a);
    }
    return result;
  }

  public static boolean overlaps(
      ServerSubLevel body, List<SubLevelLegs.Pose> poses, AABB obstacle, double tolerance) {
    var t = obstacle.deflate(tolerance);
    var c = new Vector3d((t.minX + t.maxX) / 2, (t.minY + t.maxY) / 2, (t.minZ + t.maxZ) / 2);
    var e = new Vector3d(t.getXsize() / 2, t.getYsize() / 2, t.getZsize() / 2);
    for (var b : boxes(body, poses)) {
      var q = b.rotation;
      if (Intersectiond.testObOb(
          b.center,
          q.transform(new Vector3d(1, 0, 0)),
          q.transform(new Vector3d(0, 1, 0)),
          q.transform(new Vector3d(0, 0, 1)),
          b.extent,
          c,
          new Vector3d(1, 0, 0),
          new Vector3d(0, 1, 0),
          new Vector3d(0, 0, 1),
          e)) {
        return true;
      }
    }
    return false;
  }
}
