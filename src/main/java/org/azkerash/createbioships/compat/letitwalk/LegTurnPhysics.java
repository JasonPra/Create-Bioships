package org.azkerash.createbioships.compat.letitwalk;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.locomotion.LegTurnDrive;
import org.joml.Vector3d;

/** Applies stance forces at feet, with the opposite impulse on movable support platforms. */
final class LegTurnPhysics {
  private LegTurnPhysics() {}

  static void apply(
      ServerSubLevel body, List<LegTurnDrive.Foot> feet, Vec3 normal, double requested, double dt) {
    var handle = RigidBodyHandle.of(body);
    if (handle == null || !handle.isValid()) {
      return;
    }
    var mass = body.getMassTracker();
    var axis = new Vector3d(normal.x, normal.y, normal.z);
    var local = body.logicalPose().orientation().transformInverse(new Vector3d(axis));
    double inertia =
        Math.max(0, local.dot(mass.getInertiaTensor().transform(local, new Vector3d())));
    var impulses =
        LegTurnDrive.impulses(
            feet,
            normal,
            mass.getMass(),
            inertia,
            handle.getAngularVelocity(new Vector3d()).dot(axis),
            requested,
            dt);
    var container = SubLevelContainer.getContainer(body.getLevel());
    for (int i = 0; i < feet.size(); i++) {
      var foot = feet.get(i);
      var impulse = impulses.get(i);
      if (impulse.lengthSqr() == 0) {
        continue;
      }
      applyAt(body, handle, impulse, foot.point());
      var support =
          container == null || foot.support() == null
              ? null
              : container.getSubLevel(foot.support());
      if (support instanceof ServerSubLevel platform && !platform.isRemoved()) {
        var other = RigidBodyHandle.of(platform);
        if (other != null && other.isValid()) {
          applyAt(platform, other, impulse.scale(-1), foot.point());
        }
      }
    }
  }

  private static void applyAt(
      ServerSubLevel body, RigidBodyHandle handle, Vec3 impulse, Vec3 point) {
    var force = new Vector3d(impulse.x, impulse.y, impulse.z);
    var lever = new Vector3d(point.x, point.y, point.z).sub(body.logicalPose().position());
    org.azkerash.createbioships.core.physics.SubLevelImpulse.applyWorld(
        body, handle, force, lever.cross(force, new Vector3d()));
  }
}
