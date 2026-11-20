package org.Azkerash.create_bioships.compat.letitwalk;

import dev.letitwalk.core.JointChain;
import dev.letitwalk.core.LegGait;
import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.vision.VisionRaycaster;

/** Uses Let It Walk's native constrained IK and terrain probing, without a hidden walker core. */
public final class CrabLegKinematics {
  public static final LegGait GAIT = new LegGait(false, 2.4, 1.45, .8, .35, 1);
  public final int height;
  public final double scale;
  public final double extension;
  private final JointChain chain;

  public CrabLegKinematics(int height) {
    this((double) height);
  }

  public CrabLegKinematics(double extension) {
    this.extension = Math.clamp(extension, 1, 3);
    this.height = (int) Math.round(this.extension);
    this.scale = (this.extension + .5) / 1.45;
    chain =
        JointChain.forLeg(
            new double[][] {
              {0, 0, 0}, {.65 * scale, .25 * scale, 0}, {1.25 * scale, -.45 * scale, 0}
            },
            new double[][] {{0, 1, 0}, {0, 0, 1}, {0, 0, 1}},
            new double[] {1.45 * scale, -1.45 * scale, 0},
            new boolean[] {true, false, false},
            new double[] {0, 0, 0});
  }

  public double standingHeight() {
    return extension + .5;
  }

  public static double thickness(int height) {
    return Math.sqrt((Math.clamp(height, 1, 3) + .5) / 1.45);
  }

  private final double[] angles = new double[3];

  public List<Vec3> solve(Vec3 root, Direction outward, Vec3 foot) {
    var side = Vec3.atLowerCornerOf(outward.getNormal());
    var tangent = side.cross(new Vec3(0, 1, 0));
    var relative = foot.subtract(root);
    chain.solveRobust(
        new double[] {relative.dot(side), relative.y, relative.dot(tangent)}, angles, 10, .025);
    var points = chain.forward(angles, null);
    var result = new ArrayList<Vec3>();
    for (var p : points) {
      result.add(root.add(side.scale(p[0])).add(0, p[1], 0).add(tangent.scale(p[2])));
    }
    return List.copyOf(result);
  }

  public TerrainProbe.Ground ground(ServerSubLevel body, Vec3 desired) {
    var from = desired.add(0, standingHeight(), 0);
    double depth = standingHeight() * 2 + 1;
    if (VisionRaycaster.loadedDistance(body.getLevel(), from, new Vec3(0, -1, 0), depth) < depth) {
      return null;
    }
    var ground = TerrainProbe.down(body.getLevel(), from, depth, body);
    return ground != null && TerrainProbe.standable(ground) ? ground : null;
  }
}
