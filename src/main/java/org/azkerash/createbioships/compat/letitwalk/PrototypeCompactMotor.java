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
import org.azkerash.createbioships.core.locomotion.prototype.*;
import org.azkerash.createbioships.core.vision.VisionRaycaster;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Sable bridge for the independent gait. Native planning and suspension do not run in this mode.
 */
final class PrototypeCompactMotor {
  record Binding(PrototypeGait.Leg leg, List<ServerSubLevel> segments) {}

  private record Previous(Vector3d position, Quaterniond turn) {}

  private static final double MAX_BONE_SPEED = 40;
  private final ServerSubLevel body;
  private final PrototypeGait gait = new PrototypeGait();
  private final Map<UUID, Previous> previous = new HashMap<>();
  private long physicsTick = Long.MIN_VALUE;
  private double fraction;

  PrototypeCompactMotor(ServerSubLevel body) {
    this.body = body;
  }

  void clear() {
    gait.clear();
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

  void update(List<Binding> bindings) {
    var ids = new HashSet<UUID>();
    bindings.forEach(binding -> binding.segments.forEach(part -> ids.add(part.getUniqueId())));
    previous.keySet().retainAll(ids);
    var family = new HashSet<UUID>();
    SubLevelCreatureParts.members(body).forEach(part -> family.add(part.getUniqueId()));
    TerrainProbe.withIgnored(
        part -> family.contains(part.getUniqueId()),
        () ->
            gait.update(
                bindings.stream().map(Binding::leg).toList(),
                BrainMotorControls.current(body).velocity(),
                body.getLevel().getGameTime(),
                terrain));
  }

  int contacts() {
    return gait.contacts();
  }

  void physics(List<Binding> bindings, double dt) {
    if (!(dt > 0 && dt <= .1)) {
      return;
    }
    var handle = RigidBodyHandle.of(body);
    if (handle == null || !handle.isValid()) {
      return;
    }
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
    var delta =
        PrototypeBodyMotor.calculate(
            vec(velocity),
            vec(spin),
            vec(up),
            request.velocity(),
            request.yawRate(),
            gait.supportHeight(legs, y) - y,
            dt,
            gait.contacts() >= Math.max(2, legs.size() / 2));
    handle.addLinearAndAngularVelocity(v(delta.velocity()), v(delta.angularVelocity()));
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
