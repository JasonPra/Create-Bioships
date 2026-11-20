package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Collision admission for native powered poses. Native gait, IK and suspension remain unchanged.
 */
public final class ModularLimbCollisions {
  private static final Map<ServerSubLevel, Long> BLOCKED = new WeakHashMap<>();
  private static final Map<BuiltLeg, Long> BRACE = new WeakHashMap<>();
  private static final Map<BuiltLeg, double[]> ADMITTED = new WeakHashMap<>();

  public static boolean obstructed(ServerSubLevel owner) {
    return owner.getLevel().getGameTime() < BLOCKED.getOrDefault(owner, 0L);
  }

  public static boolean bracing(BuiltLeg leg, long time) {
    return time < BRACE.getOrDefault(leg, 0L);
  }

  private ModularLimbCollisions() {}

  /** Reject a complete leg together so a blocked child cannot detach visually from its parent. */
  public static Map<UUID, WalkerCargo.Pose> substep(
      WalkerCoreBlockEntity walker, ServerSubLevel owner, double dt) {
    var all = new ArrayList<>(walker.legs);
    try {
      walker.legs.removeIf(
          leg -> {
            if (admit(owner, walker.getBlockPos(), leg.built)) {
              return false;
            }
            BRACE.put(leg.built, owner.getLevel().getGameTime() + 2);
            leg.built.prevDesired = null;
            leg.built.prevDesiredRot = null;
            BLOCKED.put(owner, owner.getLevel().getGameTime() + 20);
            var request = BrainMotorControls.current(owner);
            if (!org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.movementClear(
                owner, request.velocity(), request.yawRate())) {
              BrainMotorControls.obstructed(owner);
            }
            return true;
          });
      return BuiltLegDriver.substep(walker, owner, dt);
    } finally {
      walker.legs.clear();
      walker.legs.addAll(all);
    }
  }

  /** Keep native actuation at a feasible pose when an IK iteration grazes terrain. */
  private static boolean admit(ServerSubLevel owner, BlockPos core, BuiltLeg built) {
    if (clear(owner, core, built)) {
      ADMITTED.put(built, built.angles.clone());
      return true;
    }
    var previous = ADMITTED.get(built);
    if (previous == null || previous.length != built.angles.length) {
      return false;
    }
    var desired = built.angles.clone();
    var limits = (org.azkerash.createbioships.mixin.JointChainLimitsAccess) (Object) built.chain;
    for (double fraction : new double[] {.5, .25, .125, 0}) {
      for (int i = 0; i < desired.length; i++) {
        built.angles[i] =
            Math.clamp(
                previous[i] + fraction * (desired[i] - previous[i]),
                limits.bioships$minimum()[i],
                limits.bioships$maximum()[i]);
      }
      if (clear(owner, core, built)) {
        ADMITTED.put(built, built.angles.clone());
        return true;
      }
    }
    System.arraycopy(desired, 0, built.angles, 0, desired.length);
    return false;
  }

  /** Read the same forward-chain poses used by BuiltLegDriver, including socket rotation axes. */
  public static boolean clear(ServerSubLevel owner, BlockPos core, BuiltLeg built) {
    var container = SubLevelContainer.getContainer(owner.getLevel());
    if (container == null) {
      return false;
    }
    var points = built.chain.forward(built.angles, null);
    var rotation = new Quaterniond();
    int n =
        Math.min(Math.min(built.segments.size(), built.joints.size()), built.segmentOffsets.length);
    for (int i = 0; i < n; i++) {
      var axis = built.restAxes[i];
      rotation.mul(new Quaterniond().fromAxisAngleRad(axis[0], axis[1], axis[2], built.angles[i]));
      // Two socket DOFs can reference one physical body; only the final pose is rendered/driven.
      if (built.segments.subList(i + 1, n).contains(built.segments.get(i))) {
        continue;
      }
      if (!(container.getSubLevel(built.segments.get(i)) instanceof ServerSubLevel part)) {
        return false;
      }
      var origin = part.logicalPose().rotationPoint();
      var off = built.segmentOffsets[i];
      var pivot = built.restPivots[i];
      var point = points[i];
      var relative =
          rotation
              .transform(
                  new Vector3d(
                      origin.x() + off.x - pivot[0],
                      origin.y() + off.y - pivot[1],
                      origin.z() + off.z - pivot[2]))
              .add(point[0], point[1], point[2]);
      var desired =
          owner
              .logicalPose()
              .transformPosition(
                  Vec3.atLowerCornerOf(core).add(relative.x, relative.y, relative.z));
      var turn = new Quaterniond(owner.logicalPose().orientation()).mul(rotation);
      if (!clearSweep(part, desired, turn)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finite-volume swept tests prevent a native pose teleport from tunnelling through a thin wall.
   */
  public static boolean clearSweep(ServerSubLevel part, Vec3 desired, Quaterniondc turn) {
    var pose = part.logicalPose();
    var from = new Vector3d(pose.position());
    var to = new Vector3d(desired.x, desired.y, desired.z);
    var shapes = LimbGroundContacts.shapes(part);
    var origin = pose.rotationPoint();
    var scale = pose.scale();
    double radius = 0;
    for (var box : shapes) {
      radius =
          java.lang.Math.max(
              radius,
              new Vector3d(
                      java.lang.Math.max(
                              java.lang.Math.abs(box.minX - origin.x()),
                              java.lang.Math.abs(box.maxX - origin.x()))
                          * scale.x(),
                      java.lang.Math.max(
                              java.lang.Math.abs(box.minY - origin.y()),
                              java.lang.Math.abs(box.maxY - origin.y()))
                          * scale.y(),
                      java.lang.Math.max(
                              java.lang.Math.abs(box.minZ - origin.z()),
                              java.lang.Math.abs(box.maxZ - origin.z()))
                          * scale.z())
                  .length());
    }
    double dot =
        java.lang.Math.min(1, java.lang.Math.abs(new Quaterniond(pose.orientation()).dot(turn)));
    int steps =
        java.lang.Math.max(
            1,
            (int)
                java.lang.Math.ceil(
                    (from.distance(to) + radius * 2 * java.lang.Math.acos(dot)) / .125));
    if (steps > 64) {
      return false;
    }
    for (int step = 1; step <= steps; step++) {
      double t = step / (double) steps;
      var position = new Vector3d(from).lerp(to, t);
      var rotation = new Quaterniond(pose.orientation()).slerp(turn, t);
      var x = rotation.transform(new Vector3d(1, 0, 0));
      var y = rotation.transform(new Vector3d(0, 1, 0));
      var z = rotation.transform(new Vector3d(0, 0, 1));
      for (var box : shapes) {
        var center =
            rotation
                .transform(
                    new Vector3d(
                            (box.minX + box.maxX) / 2 - origin.x(),
                            (box.minY + box.maxY) / 2 - origin.y(),
                            (box.minZ + box.maxZ) / 2 - origin.z())
                        .mul(scale))
                .add(position);
        var extent =
            new Vector3d(
                box.getXsize() * scale.x() / 2,
                box.getYsize() * scale.y() / 2,
                box.getZsize() * scale.z() / 2);
        var reach =
            new Vector3d(
                java.lang.Math.abs(x.x) * extent.x
                    + java.lang.Math.abs(y.x) * extent.y
                    + java.lang.Math.abs(z.x) * extent.z,
                java.lang.Math.abs(x.y) * extent.x
                    + java.lang.Math.abs(y.y) * extent.y
                    + java.lang.Math.abs(z.y) * extent.z,
                java.lang.Math.abs(x.z) * extent.x
                    + java.lang.Math.abs(y.z) * extent.y
                    + java.lang.Math.abs(z.z) * extent.z);
        var lo = BlockPos.containing(center.x - reach.x, center.y - reach.y, center.z - reach.z);
        var hi = BlockPos.containing(center.x + reach.x, center.y + reach.y, center.z + reach.z);
        if (!part.getLevel().hasChunksAt(lo, hi)) {
          return false;
        }
        for (var cell : BlockPos.betweenClosed(lo, hi)) {
          for (var solid :
              part.getLevel()
                  .getBlockState(cell)
                  .getCollisionShape(part.getLevel(), cell)
                  .toAabbs()) {
            var obstacle = solid.move(cell).deflate(.025);
            double depth = penetration(center, x, y, z, extent, obstacle);
            if (depth <= 0) {
              continue;
            }
            // Native physics permits a small resting contact. A sweep out of that contact
            // must remain possible; rejecting its first sample strands the foot in ragdoll.
            var initialRotation = new Quaterniond(pose.orientation());
            var initialCenter =
                initialRotation
                    .transform(
                        new Vector3d(
                                (box.minX + box.maxX) / 2 - origin.x(),
                                (box.minY + box.maxY) / 2 - origin.y(),
                                (box.minZ + box.maxZ) / 2 - origin.z())
                            .mul(scale))
                    .add(from);
            double initialDepth =
                penetration(
                    initialCenter,
                    initialRotation.transform(new Vector3d(1, 0, 0)),
                    initialRotation.transform(new Vector3d(0, 1, 0)),
                    initialRotation.transform(new Vector3d(0, 0, 1)),
                    extent,
                    obstacle);
            if (initialDepth <= 0 || depth > initialDepth + 1e-7) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  /** Separating-axis penetration of an oriented limb box against a world collision box. */
  private static double penetration(
      Vector3d center, Vector3d x, Vector3d y, Vector3d z, Vector3d extent, AABB obstacle) {
    var world =
        new Vector3d[] {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)};
    var local = new Vector3d[] {x, y, z};
    var axes = new ArrayList<Vector3d>(15);
    Collections.addAll(axes, world);
    Collections.addAll(axes, local);
    for (var a : local) {
      for (var b : world) {
        axes.add(a.cross(b, new Vector3d()));
      }
    }
    var delta =
        new Vector3d(
                (obstacle.minX + obstacle.maxX) / 2,
                (obstacle.minY + obstacle.maxY) / 2,
                (obstacle.minZ + obstacle.maxZ) / 2)
            .sub(center);
    double minimum = Double.POSITIVE_INFINITY;
    for (var axis : axes) {
      if (axis.lengthSquared() < 1e-12) {
        continue;
      }
      var a = new Vector3d(axis).normalize();
      double radius =
          Math.abs(a.dot(x)) * extent.x
              + Math.abs(a.dot(y)) * extent.y
              + Math.abs(a.dot(z)) * extent.z
              + Math.abs(a.x) * obstacle.getXsize() / 2
              + Math.abs(a.y) * obstacle.getYsize() / 2
              + Math.abs(a.z) * obstacle.getZsize() / 2;
      double depth = radius - Math.abs(a.dot(delta));
      if (depth <= 0) {
        return 0;
      }
      minimum = Math.min(minimum, depth);
    }
    return minimum;
  }
}
