package org.azkerash.createbioships.core.flippers;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Flipper-local geometry in blocks: a fixed socket and three gently flexing paddle sections. */
public final class FlipperGeometry {
  public static final int SECTIONS = 3;
  public static final double LENGTH = 2;
  public static final double SOCKET_OFFSET = .08;
  public static final double SECTION_LENGTH = (LENGTH - SOCKET_OFFSET) / SECTIONS;
  private static final double REST_ANGLE = Math.toRadians(-10);
  private static final double IDLE_SWEEP = Math.toRadians(3);
  private static final double IDLE_LIFT = Math.toRadians(2);
  private static final double ROWING_SWEEP = Math.toRadians(24);
  private static final double FEATHER_LIFT = Math.toRadians(12);
  private static final double TIP_FLEX = Math.toRadians(5);
  private static final Vec3 UP = new Vec3(0, 1, 0);

  private FlipperGeometry() {}

  public static Vec3 outward(BlockState state) {
    return Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal());
  }

  public static List<Vec3> rest(BlockState state) {
    var outward = outward(state);
    var direction = outward.scale(Math.cos(REST_ANGLE)).add(UP.scale(Math.sin(REST_ANGLE)));
    var root = new Vec3(.5, .5, .5).subtract(outward.scale(.5 - SOCKET_OFFSET));
    var points = new ArrayList<Vec3>();
    for (int i = 0; i <= SECTIONS; i++) {
      points.add(root.add(direction.scale(SECTION_LENGTH * i)));
    }
    return List.copyOf(points);
  }

  /** Mirrored power/recovery strokes, with no leg tuck, solver seeds or knee-style folding. */
  public static List<Vec3> stroke(BlockState state, double phase, double drive, Direction facing) {
    var outward = outward(state);
    var forward = Vec3.atLowerCornerOf(facing.getNormal());
    double handedness = outward.cross(forward).y;
    if (handedness == 0) {
      handedness = 1;
    }
    drive = Math.clamp(drive, 0, 1);
    double sweep = (IDLE_SWEEP + ROWING_SWEEP * drive) * Math.sin(phase) * handedness;
    double feather = FEATHER_LIFT * drive * Math.max(0, Math.cos(phase));
    var heading = outward.yRot((float) sweep).normalize();
    var points = new ArrayList<Vec3>();
    var point = rest(state).getFirst();
    points.add(point);
    for (int i = 0; i < SECTIONS; i++) {
      double bend =
          REST_ANGLE
              + IDLE_LIFT * (1 - drive) * Math.cos(phase)
              + feather
              + TIP_FLEX * drive * i / (SECTIONS - 1d) * Math.sin(phase);
      var direction = heading.scale(Math.cos(bend)).add(UP.scale(Math.sin(bend)));
      point = point.add(direction.scale(SECTION_LENGTH));
      points.add(point);
    }
    return List.copyOf(points);
  }

  /** Cached by block orientation: one conservative collider covers the whole rowing envelope. */
  public static AABB sweptBounds(BlockState state) {
    // Half widths/thickness in model pixels, converted to blocks; matches the three paddle models.
    double[] halfWidths = {5.3 / 16, 6.2 / 16, 5.25 / 16};
    double halfThickness = .9 / 16;
    var root = rest(state).getFirst();
    var tip = root.add(outward(state).scale(LENGTH - SOCKET_OFFSET));
    var bounds = new AABB(root, tip);
    final int phaseSamples = 64;
    for (double drive : new double[] {0, .5, 1}) {
      for (int sample = 0; sample < phaseSamples; sample++) {
        var pose = stroke(state, sample * Math.PI * 2 / phaseSamples, drive, Direction.NORTH);
        for (int section = 0; section < SECTIONS; section++) {
          var rotation = orientation(pose.get(section + 1).subtract(pose.get(section)));
          for (int x : new int[] {-1, 1}) {
            for (int y : new int[] {-1, 1}) {
              for (int z = 0; z <= 1; z++) {
                var vertex =
                    rotation.transform(
                        new Vector3f(
                            (float) (x * halfWidths[section]),
                            (float) (y * halfThickness),
                            (float) (z * SECTION_LENGTH)));
                var point = pose.get(section).add(vertex.x, vertex.y, vertex.z);
                bounds = bounds.minmax(new AABB(point, point));
              }
            }
          }
        }
      }
    }
    // Cover continuous phases between samples and renderer float rounding.
    return bounds.inflate(.02);
  }

  /** Interpolating endpoints directly shrinks the paddle between network samples. */
  public static List<Vec3> interpolate(List<Vec3> previous, List<Vec3> next, double fraction) {
    var points = new ArrayList<Vec3>();
    var point = previous.getFirst().lerp(next.getFirst(), fraction);
    points.add(point);
    for (int i = 0; i < SECTIONS; i++) {
      var from = previous.get(i + 1).subtract(previous.get(i)).normalize();
      var to = next.get(i + 1).subtract(next.get(i)).normalize();
      point = point.add(from.lerp(to, fraction).normalize().scale(SECTION_LENGTH));
      points.add(point);
    }
    return List.copyOf(points);
  }

  /** Fix roll relative to the creature's local up; shortest-arc rotations flip near local north. */
  public static Quaternionf orientation(Vec3 delta) {
    var forward = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize();
    var right = new Vector3f(forward.z, 0, -forward.x);
    if (right.lengthSquared() < 1e-8f) {
      right.set(1, 0, 0);
    }
    right.normalize();
    var up = new Vector3f(forward).cross(right).normalize();
    return new Quaternionf()
        .setFromNormalized(
            new Matrix3f().setColumn(0, right).setColumn(1, up).setColumn(2, forward));
  }
}
