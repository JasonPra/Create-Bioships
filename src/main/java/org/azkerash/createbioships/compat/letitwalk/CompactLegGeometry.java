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
    // Coordinates are blocks at the base scale: huntsman legs retain their low, broad knee.
    return arachnid
        ? List.of(
            start,
            start.add(side.scale(.65 * scale)).add(0, .25 * scale, 0),
            start.add(side.scale(1.65 * scale)).add(0, -.2 * scale, 0),
            start.add(side.scale(2.15 * scale)).add(0, -1.7 * scale, 0))
        : List.of(
            start,
            start.add(side.scale(.65 * scale)).add(0, .25 * scale, 0),
            start.add(side.scale(1.25 * scale)).add(0, -.45 * scale, 0),
            start.add(side.scale(1.45 * scale)).add(0, -1.45 * scale / .85, 0));
  }
}
