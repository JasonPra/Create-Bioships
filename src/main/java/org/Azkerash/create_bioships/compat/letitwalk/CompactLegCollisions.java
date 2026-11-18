package org.Azkerash.create_bioships.compat.letitwalk;

import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Native oriented collision boxes following the same three segments as the compact-leg mesh. */
public final class CompactLegCollisions {
  private record Segment(BoxPhysicsObject box, GenericConstraintHandle attachment, double length) {}

  private static final double[] WIDTHS = {.23, .17, .09};

  private static final class Limb {
    final List<Segment> segments = new ArrayList<>();
    final List<GenericConstraintHandle> filters = new ArrayList<>();
    int height;
    boolean powered;
  }

  private final ServerSubLevel body;
  private final Map<BlockPos, Limb> limbs = new HashMap<>();

  public CompactLegCollisions(ServerSubLevel body) {
    this.body = body;
  }

  public static double width(int segment, int height) {
    return WIDTHS[segment] * CrabLegKinematics.thickness(height);
  }

  public void retain(Set<BlockPos> cells) {
    limbs
        .entrySet()
        .removeIf(
            e -> {
              if (cells.contains(e.getKey())) return false;
              remove(e.getValue());
              return true;
            });
  }

  public void update(BlockPos cell, int height, List<Vec3> joints, boolean powered) {
    if (joints.size() != 4) return;
    var system = SubLevelPhysicsSystem.get(body.getLevel());
    if (system == null || body.isRemoved()) return;
    var limb = limbs.get(cell);
    var previousPoses = new ArrayList<Pose3d>();
    if (limb != null)
      for (var segment : limb.segments) {
        if (segment.box.isRemoved()) {
          previousPoses.clear();
          break;
        }
        segment.box.updatePose();
        previousPoses.add(new Pose3d(segment.box.getPose()));
      }
    if (limb != null
        && (limb.height != height
            || java.util.stream.IntStream.range(0, 3)
                .anyMatch(
                    i ->
                        Math.abs(limbLength(cell, i) - joints.get(i).distanceTo(joints.get(i + 1)))
                            > .035)
            || limb.segments.stream()
                .anyMatch(s -> s.box.isRemoved() || !s.attachment.isValid()))) {
      remove(limb);
      limbs.remove(cell);
      limb = null;
    }
    if (limb == null) {
      limb = new Limb();
      limb.height = height;
      limbs.put(cell.immutable(), limb);
      for (int i = 0; i < 3; i++) {
        var a = joints.get(i);
        var b = joints.get(i + 1);
        var delta = b.subtract(a);
        double length = delta.length();
        if (length < .001) {
          remove(limb);
          limbs.remove(cell);
          return;
        }
        var center = a.add(b).scale(.5);
        var orientation =
            new Quaterniond().rotationTo(new Vector3d(0, 1, 0), vector(delta).normalize());
        var world = body.logicalPose().transformPosition(center);
        var pose = previousPoses.size() == 3 ? previousPoses.get(i) : new Pose3d();
        if (previousPoses.size() != 3) {
          pose.position().set(world.x, world.y, world.z);
          pose.orientation().set(body.logicalPose().orientation()).mul(orientation);
        }
        var box =
            new BoxPhysicsObject(
                pose, new Vector3d(width(i, height) / 2, length / 2, width(i, height) / 2), .25);
        system.addObject(box);
        var attachment =
            system
                .getPipeline()
                .addConstraint(
                    body,
                    box,
                    new GenericConstraintConfiguration(
                        vector(center),
                        new Vector3d(),
                        orientation,
                        new Quaterniond(),
                        EnumSet.noneOf(ConstraintJointAxis.class)));
        if (attachment == null) {
          system.removeObject(box);
          remove(limb);
          limbs.remove(cell);
          return;
        }
        attachment.setContactsEnabled(false);
        limb.segments.add(new Segment(box, attachment, length));
      }
      // Actual ball joints keep the bones connected. Angular servos can yield
      // to terrain contacts instead of forcing a rigid animation through rock.
      var mount =
          system
              .getPipeline()
              .addConstraint(
                  body,
                  limb.segments.getFirst().box,
                  new GenericConstraintConfiguration(
                      vector(joints.getFirst()),
                      new Vector3d(0, -limb.segments.getFirst().length / 2, 0),
                      new Quaterniond(),
                      new Quaterniond(),
                      EnumSet.copyOf(Arrays.asList(ConstraintJointAxis.LINEAR))));
      if (mount != null) {
        mount.setContactsEnabled(false);
        limb.filters.add(mount);
      }
      for (int i = 0; i < 2; i++) {
        var first = limb.segments.get(i);
        var second = limb.segments.get(i + 1);
        var joint =
            system
                .getPipeline()
                .addConstraint(
                    first.box,
                    second.box,
                    new GenericConstraintConfiguration(
                        new Vector3d(0, first.length / 2, 0),
                        new Vector3d(0, -second.length / 2, 0),
                        new Quaterniond(),
                        new Quaterniond(),
                        EnumSet.copyOf(Arrays.asList(ConstraintJointAxis.LINEAR))));
        if (joint != null) {
          joint.setContactsEnabled(false);
          limb.filters.add(joint);
        }
      }
    }
    if (limb.powered != powered) {
      double torque = Math.clamp(body.getMassTracker().getMass() * (height + .5) * 4, 30, 600);
      for (var segment : limb.segments)
        for (var axis : ConstraintJointAxis.ANGULAR)
          segment.attachment.setMotor(
              axis, 0, powered ? 1000 : 0, powered ? 50 : 0, true, powered ? torque : 0);
      limb.powered = powered;
    }
    for (int i = 0; i < 3; i++) {
      limb.segments.get(i).box.updatePose();
      var a = joints.get(i);
      var b = joints.get(i + 1);
      var delta = b.subtract(a);
      if (delta.lengthSqr() < .000001) continue;
      var orientation =
          new Quaterniond().rotationTo(new Vector3d(0, 1, 0), vector(delta).normalize());
      limb.segments.get(i).attachment.setFrame1(vector(a.add(b).scale(.5)), orientation);
    }
  }

  /** Actual native centers/axes, rather than an animation target which terrain may block. */
  public List<Vec3> endpoints(BlockPos cell, List<Vec3> fallback) {
    var limb = limbs.get(cell);
    if (limb == null
        || limb.segments.size() != 3
        || limb.segments.stream().anyMatch(s -> s.box.isRemoved())) {
      var result = new ArrayList<Vec3>();
      for (int i = 0; i < 3; i++) {
        result.add(fallback.get(i));
        result.add(fallback.get(i + 1));
      }
      return List.copyOf(result);
    }
    var result = new ArrayList<Vec3>();
    for (var segment : limb.segments) {
      segment.box.updatePose();
      var pose = segment.box.getPose();
      var offset = pose.orientation().transform(new Vector3d(0, segment.length / 2, 0));
      var center = pose.position();
      result.add(
          body.logicalPose()
              .transformPositionInverse(
                  new Vec3(center.x() - offset.x, center.y() - offset.y, center.z() - offset.z)));
      result.add(
          body.logicalPose()
              .transformPositionInverse(
                  new Vec3(center.x() + offset.x, center.y() + offset.y, center.z() + offset.z)));
    }
    return List.copyOf(result);
  }

  private double limbLength(BlockPos cell, int i) {
    return limbs.get(cell).segments.get(i).length;
  }

  public boolean grounded(BlockPos cell) {
    var limb = limbs.get(cell);
    if (limb == null || limb.segments.size() != 3) return false;
    var foot = limb.segments.getLast();
    if (foot.box.isRemoved()) return false;
    foot.box.updatePose();
    var pose = foot.box.getPose();
    var offset = pose.orientation().transform(new Vector3d(0, foot.length / 2, 0));
    var toe =
        new Vec3(
            pose.position().x() + offset.x,
            pose.position().y() + offset.y,
            pose.position().z() + offset.z);
    if (!body.getLevel().hasChunkAt(BlockPos.containing(toe))) return false;
    var ground =
        dev.letitwalk.walker.TerrainProbe.down(body.getLevel(), toe.add(0, .15, 0), .4, body);
    return ground != null
        && dev.letitwalk.walker.TerrainProbe.standable(ground)
        && Math.abs(toe.y - ground.point().y) < width(2, limb.height) / 2 + .12;
  }

  public boolean overlaps(net.minecraft.world.phys.AABB obstacle, double tolerance) {
    var test = obstacle.deflate(tolerance);
    var center =
        new Vector3d(
            (test.minX + test.maxX) / 2, (test.minY + test.maxY) / 2, (test.minZ + test.maxZ) / 2);
    var extent = new Vector3d(test.getXsize() / 2, test.getYsize() / 2, test.getZsize() / 2);
    for (var limb : limbs.values())
      for (var segment : limb.segments) {
        if (segment.box.isRemoved()) continue;
        segment.box.updatePose();
        var pose = segment.box.getPose();
        var q = pose.orientation();
        if (org.joml.Intersectiond.testObOb(
            new Vector3d(pose.position()),
            q.transform(new Vector3d(1, 0, 0)),
            q.transform(new Vector3d(0, 1, 0)),
            q.transform(new Vector3d(0, 0, 1)),
            new Vector3d(segment.box.getHalfExtents()),
            center,
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 1),
            extent)) return true;
      }
    return false;
  }

  public net.minecraft.world.phys.AABB bounds() {
    net.minecraft.world.phys.AABB result = null;
    for (var limb : limbs.values())
      for (var segment : limb.segments) {
        if (segment.box.isRemoved()) continue;
        segment.box.updatePose();
        var pose = segment.box.getPose();
        var q = pose.orientation();
        var h = segment.box.getHalfExtents();
        var c = pose.position();
        var x = q.transform(new Vector3d(1, 0, 0));
        var y = q.transform(new Vector3d(0, 1, 0));
        var z = q.transform(new Vector3d(0, 0, 1));
        double ex = Math.abs(x.x) * h.x() + Math.abs(y.x) * h.y() + Math.abs(z.x) * h.z();
        double ey = Math.abs(x.y) * h.x() + Math.abs(y.y) * h.y() + Math.abs(z.y) * h.z();
        double ez = Math.abs(x.z) * h.x() + Math.abs(y.z) * h.y() + Math.abs(z.z) * h.z();
        var box =
            new net.minecraft.world.phys.AABB(
                c.x() - ex, c.y() - ey, c.z() - ez, c.x() + ex, c.y() + ey, c.z() + ez);
        result = result == null ? box : result.minmax(box);
      }
    return result;
  }

  public int count() {
    return limbs.values().stream().mapToInt(l -> l.segments.size()).sum();
  }

  public void close() {
    limbs.values().forEach(this::remove);
    limbs.clear();
  }

  private void remove(Limb limb) {
    for (var filter : limb.filters) if (filter.isValid()) filter.remove();
    for (var segment : limb.segments) {
      if (segment.attachment.isValid()) segment.attachment.remove();
      if (!segment.box.isRemoved())
        SubLevelPhysicsSystem.require(body.getLevel()).removeObject(segment.box);
    }
    limb.filters.clear();
    limb.segments.clear();
  }

  private static Vector3d vector(Vec3 p) {
    return new Vector3d(p.x, p.y, p.z);
  }
}
