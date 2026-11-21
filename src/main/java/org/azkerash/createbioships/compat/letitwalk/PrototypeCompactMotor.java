package org.azkerash.createbioships.compat.letitwalk;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.locomotion.SubLevelFacing;
import org.azkerash.createbioships.core.locomotion.prototype.*;
import org.azkerash.createbioships.core.vision.VisionRaycaster;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Sable bridge for the independent gait. Native planning and suspension do not run in this mode.
 */
final class PrototypeCompactMotor {
  record Binding(
      PrototypeGait.Leg leg,
      List<ServerSubLevel> segments,
      List<Vec3> restDirections,
      List<PrototypeJointPose.Limit> limits,
      Vec3 rootUp,
      double mobility) {}

  private record Previous(Vector3d position, Quaterniond turn) {}

  private static final double MAX_BONE_SPEED = 40;
  private static final double MIN_COMMAND_LENGTH_SQUARED = .001;
  private static final double WALL_TRANSITION_MAX_NORMAL_DOT = .99;
  private static final double WALL_REACH_TOLERANCE = .15;
  private static final double MIN_FRICTION_UP = .7;
  private static final double WALL_SUPPORT_MAX_UP = .99;
  private static final double EDGE_HULL_CLEARANCE = .2, MIN_EDGE_NORMAL_ALIGNMENT = .25;
  private final ServerSubLevel body;
  private final PrototypeGait gait = new PrototypeGait();
  private final Map<BlockPos, Binding> definitions = new HashMap<>();
  private final Map<UUID, Previous> previous = new HashMap<>();
  private long physicsTick = Long.MIN_VALUE;
  private double fraction;
  private Vec3 climbIntent = Vec3.ZERO;
  private long climbIntentExpires;
  private static final int CLIMB_INTENT_TICKS = 10;

  PrototypeCompactMotor(ServerSubLevel body) {
    this.body = body;
  }

  void resetRestPose(BlockPos socket) {
    gait.resetLeg(socket);
  }

  void clear() {
    gait.clear();
    climbIntent = Vec3.ZERO;
    definitions.clear();
    previous.clear();
    physicsTick = Long.MIN_VALUE;
  }

  private final PrototypeGait.Terrain terrain =
      new PrototypeGait.Terrain() {
        @Override
        public PrototypeGait.Contact probe(Vec3 desired, double height) {
          var from = desired.add(0, height, 0);
          double depth = height * 2 + 1;
          if (VisionRaycaster.loadedDistance(body.getLevel(), from, new Vec3(0, -1, 0), depth)
              < depth) {
            return null;
          }
          var hit = TerrainProbe.down(body.getLevel(), from, depth, body);
          if (hit == null || !TerrainProbe.standable(hit)) {
            return null;
          }
          // Platform-local anchors follow moving foreign sublevels without another cast.
          var inside = hit.point().subtract(hit.normal().scale(.02));
          for (var candidate :
              Sable.HELPER.getAllIntersecting(
                  body.getLevel(), new BoundingBox3d(AABB.ofSize(inside, .1, .1, .1)))) {
            if (!(candidate instanceof ServerSubLevel support)
                || support.isRemoved()
                || SubLevelCreatureParts.root(support) == body) {
              continue;
            }
            var local = support.logicalPose().transformPositionInverse(inside);
            var cell = BlockPos.containing(local);
            var shape =
                body.getLevel().getBlockState(cell).getCollisionShape(body.getLevel(), cell);
            if (shape.toAabbs().stream()
                .anyMatch(box -> box.move(cell).inflate(.02).contains(local))) {
              return new PrototypeGait.Contact(
                  hit.point(),
                  support.getUniqueId(),
                  support.logicalPose().transformPositionInverse(hit.point()));
            }
          }
          return new PrototypeGait.Contact(hit.point(), null, hit.point());
        }

        @Override
        public PrototypeGait.Contact probe(
            PrototypeGait.Leg leg, Vec3 desired, double height, PrototypeGait.Contact previous) {
          if (!PrototypeGait.anchored(leg)) {
            return probe(desired, height);
          }
          var binding = definitions.get(leg.socket());
          var up = previous == null ? up(leg) : normal(previous);
          // Recheck one short ray into the same face; never search for a replacement during a
          // recheck.
          if (previous != null) {
            return org.azkerash.createbioships.core.legs.SurfaceContactProbe.cast(
                body, desired.add(up.scale(height)), desired.subtract(up.scale(height)));
          }
          var command = BrainMotorControls.current(body).velocity();
          if (body.getLevel().getGameTime() < climbIntentExpires) {
            command = climbIntent;
          }
          var heading =
              command.lengthSqr() > MIN_COMMAND_LENGTH_SQUARED ? command.normalize() : Vec3.ZERO;
          // Front toes may transfer from floor to a wall. At most one extra acquisition ray,
          // and only when moving; idle feet do not continuously scan a ray fan.
          var centre =
              definitions.values().stream()
                  .map(b -> b.leg.hip())
                  .reduce(Vec3.ZERO, Vec3::add)
                  .scale(1d / definitions.size());
          if (heading.lengthSqr() > 0
              && (leg.hip().subtract(centre).dot(heading) >= 0
                  || up.y < WALL_TRANSITION_MAX_NORMAL_DOT)) {
            // Preserve the foot's lateral spread. A ray from the hip forces side-mounted legs
            // to fold directly under their socket, even when a reachable wall toe exists beside it.
            var wallOrigin =
                desired.subtract(heading.scale(desired.subtract(leg.hip()).dot(heading)));
            var wall =
                org.azkerash.createbioships.core.legs.SurfaceContactProbe.cast(
                    body,
                    wallOrigin,
                    wallOrigin.add(heading.scale(leg.coxa() + leg.thigh() + leg.shin())));
            if (wall != null
                && wall.normal().dot(up) < WALL_TRANSITION_MAX_NORMAL_DOT
                && solve(leg, wall.point()).getLast().distanceTo(wall.point())
                    <= WALL_REACH_TOLERANCE) {
              return wall;
            }
          }
          return org.azkerash.createbioships.core.legs.SurfaceContactProbe.cast(
              body, desired.add(up.scale(height)), desired.subtract(up.scale(height)));
        }

        @Override
        public Vec3 up(PrototypeGait.Leg leg) {
          var legs = definitions.values().stream().map(Binding::leg).toList();
          return gait.anchoredContacts(legs)
                  >= Math.max(ArachnidBodyMotor.MIN_ANCHORS, gait.requiredContacts(legs))
              ? gait.anchorNormal(legs, this)
              : definitions.get(leg.socket()).rootUp;
        }

        @Override
        public Vec3 normal(PrototypeGait.Contact contact) {
          if (contact.support() == null) {
            return contact.normal();
          }
          var container = SubLevelContainer.getContainer(body.getLevel());
          var support = container == null ? null : container.getSubLevel(contact.support());
          if (support == null || support.isRemoved()) {
            return contact.normal();
          }
          var normal = support.logicalPose().orientation().transform(v(contact.localNormal()));
          return vec(normal).normalize();
        }

        @Override
        public Vec3 velocity(PrototypeGait.Contact contact) {
          if (contact.support() == null) {
            return Vec3.ZERO;
          }
          var container = SubLevelContainer.getContainer(body.getLevel());
          var support = container == null ? null : container.getSubLevel(contact.support());
          var handle = support instanceof ServerSubLevel server ? RigidBodyHandle.of(server) : null;
          if (handle == null || !handle.isValid()) {
            return Vec3.ZERO;
          }
          var point = position(contact);
          var offset = v(point).sub(support.logicalPose().position());
          var velocity =
              handle
                  .getAngularVelocity(new Vector3d())
                  .cross(offset)
                  .add(handle.getLinearVelocity(new Vector3d()));
          return vec(velocity);
        }

        @Override
        public List<Vec3> solve(PrototypeGait.Leg leg, Vec3 target) {
          var binding = definitions.get(leg.socket());
          if (binding == null) {
            return PrototypeGait.Terrain.super.solve(leg, target);
          }
          var walking =
              PrototypeJointPose.solve(
                  leg, target, binding.restDirections, binding.rootUp, binding.limits);
          if (PrototypeGait.anchored(leg)
              && walking.getLast().distanceTo(target) > WALL_REACH_TOLERANCE) {
            // Surface transfers can need more shoulder excursion than an ordinary
            // walking pose; the saved joint windows remain hard limits.
            return PrototypeJointPose.solveFullRange(
                leg, target, binding.restDirections, binding.rootUp, binding.limits);
          }
          return walking;
        }

        @Override
        public List<Vec3> solveRecovery(PrototypeGait.Leg leg, Vec3 target) {
          var binding = definitions.get(leg.socket());
          return binding == null
              ? solve(leg, target)
              : PrototypeJointPose.solveFullRange(
                  leg, target, binding.restDirections(), binding.rootUp(), binding.limits());
        }

        @Override
        public boolean clearPose(PrototypeGait.Leg leg, List<Vec3> points) {
          var state = body.getLevel().getBlockState(leg.socket());
          return state.hasProperty(
                  org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock.HEIGHT)
              && LimbPoseClearance.clear(
                  body,
                  points,
                  state.getValue(
                      org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock
                          .HEIGHT),
                  org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock
                      .isArachnid(state));
        }

        @Override
        public Vec3 massCenter() {
          var center = Vec3.ZERO;
          double total = 0;
          for (var member : SubLevelCreatureParts.members(body)) {
            var local = member.getMassTracker().getCenterOfMass();
            double mass = member.getMassTracker().getMass();
            if (local == null || !(mass > 0)) {
              continue;
            }
            center =
                center.add(
                    member
                        .logicalPose()
                        .transformPosition(new Vec3(local.x(), local.y(), local.z()))
                        .scale(mass));
            total += mass;
          }
          return total > 0 ? center.scale(1 / total) : null;
        }

        @Override
        public double mobility(PrototypeGait.Leg leg) {
          var binding = definitions.get(leg.socket());
          return binding == null ? 0 : binding.mobility;
        }

        @Override
        public Vec3 position(PrototypeGait.Contact contact) {
          if (contact.support() == null) {
            return contact.point();
          }
          var container = SubLevelContainer.getContainer(body.getLevel());
          var support = container == null ? null : container.getSubLevel(contact.support());
          return support == null || support.isRemoved()
              ? null
              : support.logicalPose().transformPosition(contact.localPoint());
        }
      };

  private void prepare(List<Binding> bindings) {
    definitions.clear();
    bindings.forEach(binding -> definitions.put(binding.leg.socket(), binding));
  }

  void update(List<Binding> bindings) {
    prepare(bindings);
    var ids = new HashSet<UUID>();
    bindings.forEach(binding -> binding.segments.forEach(part -> ids.add(part.getUniqueId())));
    previous.keySet().retainAll(ids);
    var family = new HashSet<UUID>();
    SubLevelCreatureParts.members(body).forEach(part -> family.add(part.getUniqueId()));
    var request = BrainMotorControls.current(body);
    var intent = request.velocity();
    if (intent.lengthSqr() < MIN_COMMAND_LENGTH_SQUARED
        && body.getLevel().getGameTime() < climbIntentExpires) {
      // Collision can cancel propulsion while the feet still need to find the
      // next grip. Retain intent for placement only; never bypass motor clearance.
      intent = surfaceMotion(climbIntent);
    }
    final var placementIntent = intent;
    TerrainProbe.withIgnored(
        part -> family.contains(part.getUniqueId()),
        () ->
            gait.update(
                bindings.stream().map(Binding::leg).toList(),
                placementIntent,
                body.getLevel().getGameTime(),
                terrain,
                SubLevelFacing.world(body),
                BrainMotorControls.current(body).yawRate()));
  }

  PrototypeGait.Contact recoveryTarget(
      List<Binding> bindings, Binding binding, Vec3 foot, Vec3 obstacle) {
    prepare(bindings);
    return FootholdRecovery.nearest(binding.leg(), foot, obstacle, terrain);
  }

  Vec3 recoveryPosition(PrototypeGait.Contact contact) {
    return terrain.position(contact);
  }

  boolean recover(List<Binding> bindings, Binding binding, Vec3 obstacle) {
    prepare(bindings);
    return gait.recover(binding.leg(), obstacle, body.getLevel().getGameTime(), terrain);
  }

  void climbIntent(Vec3 movement) {
    climbIntent = movement;
    climbIntentExpires = body.getLevel().getGameTime() + CLIMB_INTENT_TICKS;
  }

  Vec3 surfaceMotion(Vec3 requested) {
    var legs = definitions.values().stream().map(Binding::leg).toList();
    if (gait.anchoredContacts(legs)
        < Math.max(ArachnidBodyMotor.MIN_ANCHORS, gait.requiredContacts(legs))) {
      return new Vec3(requested.x, 0, requested.z);
    }
    var normal = gait.movementNormal(legs, terrain, requested);
    return ArachnidBodyMotor.surfaceMotion(requested, normal);
  }

  boolean wallAttached() {
    var legs = definitions.values().stream().map(Binding::leg).toList();
    return gait.anchoredContacts(legs)
            >= Math.max(ArachnidBodyMotor.MIN_ANCHORS, gait.requiredContacts(legs))
        && gait.anchorNormal(legs, terrain).y < WALL_SUPPORT_MAX_UP;
  }

  boolean hasBalancedSupport() {
    var center = terrain.massCenter();
    return center != null
        && gait.supportsBody(
            definitions.values().stream().map(Binding::leg).toList(), terrain, center);
  }

  int contacts() {
    return gait.contacts();
  }

  private double edgeClearance(List<PrototypeGait.Leg> legs, Vec3 up, Vec3 wallNormal) {
    if (Math.abs(wallNormal.y) >= MIN_FRICTION_UP) {
      return 0;
    }
    var plane = gait.surfacePoint(legs, terrain, wallNormal);
    if (plane == null) {
      return 0;
    }
    var box = body.boundingBox();
    var nearest =
        new Vec3(
            wallNormal.x >= 0 ? box.minX() : box.maxX(),
            wallNormal.y >= 0 ? box.minY() : box.maxY(),
            wallNormal.z >= 0 ? box.minZ() : box.maxZ());
    double gap = nearest.subtract(plane).dot(wallNormal);
    // A long torso needs more room than the neutral hip-to-toe height while
    // turning around an edge. Let the supported limbs push it away from the
    // real contact plane; do not bypass the collision sweep or teleport it.
    return Math.max(0, EDGE_HULL_CLEARANCE - gap)
        / Math.max(MIN_EDGE_NORMAL_ALIGNMENT, up.dot(wallNormal));
  }

  void physics(List<Binding> bindings, double dt) {
    if (!(dt > 0 && dt <= .1)) {
      return;
    }
    var handle = RigidBodyHandle.of(body);
    if (handle == null || !handle.isValid()) {
      return;
    }
    prepare(bindings);
    long tick = body.getLevel().getGameTime();
    if (tick != physicsTick) {
      physicsTick = tick;
      fraction = 0;
    }
    fraction = Math.min(1, fraction + dt * 20);
    var legs = bindings.stream().map(Binding::leg).toList();
    var velocity = handle.getLinearVelocity(new Vector3d());
    var spin = handle.getAngularVelocity(new Vector3d());
    var up = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
    var request = BrainMotorControls.current(body);
    double y = body.logicalPose().position().y;
    boolean adhesive = legs.stream().anyMatch(PrototypeGait::anchored);
    double mobility = bindings.stream().mapToDouble(Binding::mobility).average().orElse(0);
    var delta =
        PrototypeBodyMotor.calculate(
            vec(velocity),
            vec(spin),
            vec(up),
            request.velocity().scale(mobility * gait.driveSupport(legs)),
            request.yawRate() * mobility,
            (adhesive
                    ? gait.groundSupportHeight(legs, y, terrain, MIN_FRICTION_UP)
                    : gait.supportHeight(legs, y))
                - y,
            dt,
            gait.contacts() >= gait.requiredContacts(legs),
            gait.bodyUp(legs));
    int anchors = gait.anchoredContacts(legs);
    if (adhesive) {
      if (anchors >= Math.max(ArachnidBodyMotor.MIN_ANCHORS, gait.requiredContacts(legs))) {
        var normal = gait.anchorNormal(legs, terrain);
        var driveNormal =
            body.getLevel().getGameTime() < climbIntentExpires
                ? gait.movementNormal(legs, terrain, climbIntent)
                : normal;
        delta =
            ArachnidBodyMotor.calculate(
                vec(velocity),
                vec(spin),
                vec(up),
                normal,
                gait.supportedMotion(
                        legs, terrain, ArachnidBodyMotor.tangent(request.velocity(), driveNormal))
                    .scale(mobility),
                gait.supportVelocity(legs, terrain),
                request.yawRate() * mobility,
                gait.anchorHeightError(legs, normal) + edgeClearance(legs, normal, driveNormal),
                anchors,
                dt,
                driveNormal);
      } else if (gait.groundContacts(legs, terrain, MIN_FRICTION_UP) < gait.requiredContacts(legs)
          || vec(up).y < MIN_FRICTION_UP) {
        // Two wall toes cannot masquerade as floor support when the third anchor is lost.
        delta = new PrototypeBodyMotor.Delta(Vec3.ZERO, Vec3.ZERO);
      }
    }
    var massCenter = terrain.massCenter();
    if (adhesive && (massCenter == null || !gait.supportsBody(legs, terrain, massCenter))) {
      // Adhesive toes cannot cancel gravity or right the hull when their spatial
      // support polygon misses its mass centre, even if several toes are planted.
      delta = new PrototypeBodyMotor.Delta(Vec3.ZERO, Vec3.ZERO);
    }
    var turnAxis = adhesive ? gait.anchorNormal(legs, terrain) : new Vec3(0, 1, 0);
    var upright =
        delta.angularVelocity().subtract(turnAxis.scale(delta.angularVelocity().dot(turnAxis)));
    handle.addLinearAndAngularVelocity(v(delta.velocity()), v(upright));
    LegTurnPhysics.apply(body, gait.turnSupports(legs), turnAxis, request.yawRate() * mobility, dt);
    for (var binding : bindings) {
      var points = gait.pose(binding.leg, tick - 1 + fraction, terrain);
      for (int i = 0; i < binding.segments.size(); i++) {
        var part = binding.segments.get(i);
        var bone = RigidBodyHandle.of(part);
        if (bone == null || !bone.isValid()) {
          continue;
        }
        var a = points.get(i);
        var direction = points.get(i + 1).subtract(a).normalize();
        var turn = new Quaterniond().rotationTo(new Vector3d(0, 1, 0), v(direction));
        var anchor = Vec3.atLowerCornerOf(part.getPlot().getCenterBlock()).add(.5, 0, .5);
        // Physical poses locate the center of mass; mesh bones start at the local +Y anchor.
        var offset = new Vector3d(part.logicalPose().rotationPoint()).sub(v(anchor));
        var position = turn.transform(offset).add(v(a));
        var old =
            previous.put(
                part.getUniqueId(), new Previous(new Vector3d(position), new Quaterniond(turn)));
        var travel =
            old == null ? new Vector3d(velocity) : new Vector3d(position).sub(old.position).div(dt);
        var angular = new Vector3d();
        if (old != null) {
          var rotation = new Quaterniond(turn).mul(new Quaterniond(old.turn).conjugate());
          if (rotation.w < 0) {
            rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
          }
          double length =
              Math.sqrt(
                  rotation.x * rotation.x + rotation.y * rotation.y + rotation.z * rotation.z);
          if (length > 1e-8) {
            angular
                .set(rotation.x, rotation.y, rotation.z)
                .mul(2 * Math.atan2(length, rotation.w) / (length * dt));
          }
        }
        limit(travel);
        limit(angular);
        bone.teleport(position, turn);
        bone.addLinearAndAngularVelocity(
            travel.sub(bone.getLinearVelocity(new Vector3d())),
            angular.sub(bone.getAngularVelocity(new Vector3d())));
      }
    }
  }

  private static void limit(Vector3d value) {
    if (value.lengthSquared() > MAX_BONE_SPEED * MAX_BONE_SPEED) {
      value.normalize(MAX_BONE_SPEED);
    }
  }

  private static Vector3d v(Vec3 value) {
    return new Vector3d(value.x, value.y, value.z);
  }

  private static Vec3 vec(Vector3d value) {
    return new Vec3(value.x, value.y, value.z);
  }
}
