package org.azkerash.createbioships.compat.letitwalk;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Socket-local rest geometry. Only the player's angle rotates the neutral lateral stance. */
public final class CompactLegGeometry {
  public static final int MIN_HEIGHT = 1, MAX_HEIGHT = 6;

  private CompactLegGeometry() {}

  public static List<Vec3> restPoints(
      BlockPos socket, Direction face, double height, int angle, boolean arachnid) {
    double scale = (height + .5) / 1.45;
    var normal = Vec3.atLowerCornerOf(face.getNormal());
    var side = normal.yRot((float) Math.toRadians(angle));
    var start = Vec3.atCenterOf(socket).subtract(normal.scale(.25));
    // Heteropoda maxima reference: laterigrade femur/tibia extend mostly sideways;
    // the tapered distal section continues outward instead of becoming a vertical stilt.
    // Coordinates are blocks at the base scale, preserving the player's zero/rest angle.
    return arachnid
        ? List.of(
            start,
            start.add(side.scale(1.05 * scale)).add(0, .12 * scale, 0),
            start.add(side.scale(2.15 * scale)).add(0, .55 * scale, 0),
            start.add(side.scale(3.25 * scale)).add(0, -.85 * scale, 0))
        : List.of(
            start,
            start.add(side.scale(.65 * scale)).add(0, .25 * scale, 0),
            start.add(side.scale(1.25 * scale)).add(0, -.45 * scale, 0),
            start.add(side.scale(1.45 * scale)).add(0, -1.45 * scale / .85, 0));
  }

  /** Reconnect old physical segments using their saved geometry before the versioned rebuild. */
  static List<Vec3> previousArachnidRestPoints(
      BlockPos socket, Direction face, double height, int angle) {
    double scale = (height + .5) / 1.45;
    var normal = Vec3.atLowerCornerOf(face.getNormal());
    var side = normal.yRot((float) Math.toRadians(angle));
    var start = Vec3.atCenterOf(socket).subtract(normal.scale(.25));
    return List.of(
        start,
        start.add(side.scale(.65 * scale)).add(0, .25 * scale, 0),
        start.add(side.scale(1.65 * scale)).add(0, -.2 * scale, 0),
        start.add(side.scale(2.15 * scale)).add(0, -1.7 * scale, 0));
  }
}
