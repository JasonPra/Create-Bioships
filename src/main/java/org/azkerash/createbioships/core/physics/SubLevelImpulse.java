package org.azkerash.createbioships.core.physics;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Explicit world-space force boundary for Sable's body-local impulse API. */
public final class SubLevelImpulse {
  private SubLevelImpulse() {}

  public static void applyWorld(
      ServerSubLevel body, RigidBodyHandle handle, Vector3dc linear, Vector3dc angular) {
    var orientation = body.logicalPose().orientation();
    handle.applyLinearAndAngularImpulse(
        orientation.transformInverse(linear, new Vector3d()),
        orientation.transformInverse(angular, new Vector3d()));
  }
}
