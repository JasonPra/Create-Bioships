package org.Azkerash.create_bioships.client.components;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;

/**
 * Cached continuous gyri geometry; samples use group coordinates rather than restarting per block.
 */
final class BrainSurface {
  record Vertex(Vec3 point, Vec3 normal, float shade, float u, float v) {}

  record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {}

  final BlockPos minimum;
  final Vec3 size;
  final List<Quad> quads;

  BrainSurface(Set<BlockPos> cells) {
    minimum =
        new BlockPos(
            cells.stream().mapToInt(BlockPos::getX).min().orElse(0),
            cells.stream().mapToInt(BlockPos::getY).min().orElse(0),
            cells.stream().mapToInt(BlockPos::getZ).min().orElse(0));
    size =
        new Vec3(
            cells.stream().mapToInt(BlockPos::getX).max().orElse(0) - minimum.getX() + 1,
            cells.stream().mapToInt(BlockPos::getY).max().orElse(0) - minimum.getY() + 1,
            cells.stream().mapToInt(BlockPos::getZ).max().orElse(0) - minimum.getZ() + 1);
    var result = new ArrayList<Quad>();
    for (var cell : cells)
      for (var face : Direction.values()) {
        if (cells.contains(cell.relative(face))) continue;
        var n = Vec3.atLowerCornerOf(face.getNormal());
        var right = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        var up = n.cross(right);
        var center = Vec3.atCenterOf(cell.subtract(minimum)).add(n.scale(.5));
        for (int x = 0; x < 6; x++)
          for (int y = 0; y < 6; y++) {
            var a = vertex(center, right, up, x / 6d, y / 6d, n);
            var b = vertex(center, right, up, (x + 1) / 6d, y / 6d, n);
            var c = vertex(center, right, up, (x + 1) / 6d, (y + 1) / 6d, n);
            var d = vertex(center, right, up, x / 6d, (y + 1) / 6d, n);
            result.add(new Quad(a, b, c, d));
          }
      }
    quads = List.copyOf(result);
  }

  Vec3 surface(Vec3 point) {
    var half = size.scale(.5);
    var p = point.subtract(half);
    var q = new Vec3(p.x / half.x, p.y / half.y, p.z / half.z);
    double norm =
        Math.pow(
            Math.pow(Math.abs(q.x), 3) + Math.pow(Math.abs(q.y), 3) + Math.pow(Math.abs(q.z), 3),
            1d / 3);
    // Only outer skin is rounded; internal exposed cavities remain tied to their actual blocks.
    double rounding = Math.max(1, norm);
    var base = q.scale(1 / rounding);
    double flow = 19 * base.x + 3 * Math.sin(7 * base.z) + 2 * Math.sin(8 * base.y);
    double groove =
        .025 * (1 - Math.cos(flow)) + .018 * (1 - Math.cos(17 * base.z + 2 * Math.sin(9 * base.y)));
    var direction = new Vec3(base.x / half.x, base.y / half.y, base.z / half.z).normalize();
    var result =
        half.add(base.x * half.x, base.y * half.y, base.z * half.z)
            .subtract(direction.scale(groove));
    // Longitudinal fissure separates two lobes at the top of the combined organ.
    double fissure = .10 * Math.exp(-base.x * base.x / .025) * Math.max(0, base.y);
    return result.add(0, -fissure, 0);
  }

  private Vertex vertex(Vec3 center, Vec3 right, Vec3 up, double u, double v, Vec3 face) {
    var logical = center.add(right.scale(u - .5)).add(up.scale(v - .5));
    var point = surface(logical);
    var a = surface(logical.add(right.scale(.001))).subtract(point);
    var b = surface(logical.add(up.scale(.001))).subtract(point);
    var normal = a.cross(b).normalize();
    if (normal.lengthSqr() < .01) normal = face;
    var relative = point.subtract(size.scale(.5));
    double flow =
        19 * relative.x / size.x
            + 3 * Math.sin(7 * relative.z / size.z)
            + 2 * Math.sin(8 * relative.y / size.y);
    float shade = (float) (.83 + .17 * (.5 + .5 * Math.cos(flow)));
    return new Vertex(point, normal, shade, (float) u, (float) v);
  }
}
