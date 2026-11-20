package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeJointPose;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class PrototypeJointPoseTests {
  @GameTest(template = "test_empty")
  public static void locked_joints_keep_physical_rest_pose_despite_unreachable_targets(
      GameTestHelper h) {
    var rest =
        List.of(
            new Vec3(1, .3, 0).normalize(),
            new Vec3(.6, -.7, 0).normalize(),
            new Vec3(.2, -.9, 0).normalize());
    var desired = List.of(Vec3.ZERO, new Vec3(-1, 0, 0), new Vec3(-1, 1, 0), new Vec3(-1, 1, 1));
    var locked = new PrototypeJointPose.Limit(0, 0);
    var pose =
        PrototypeJointPose.constrain(
            desired, rest, new Vec3(0, 1, 0), List.of(locked, locked, locked));
    for (int i = 0; i < 3; i++) {
      var delta = pose.get(i + 1).subtract(pose.get(i));
      h.assertTrue(
          delta.normalize().distanceTo(rest.get(i)) < 1e-7, "Locked joint left its rest frame");
      h.assertTrue(Math.abs(delta.length() - 1) < 1e-7, "Joint projection stretched a bone");
    }
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void limited_knee_bend_cannot_expand_beyond_saved_hard_window(GameTestHelper h) {
    var rest = List.of(new Vec3(1, 0, 0), new Vec3(0, -1, 0), new Vec3(0, -1, 0));
    var desired = List.of(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(1, -1, 0), new Vec3(2, -1, 0));
    var locked = new PrototypeJointPose.Limit(0, 0);
    var knee = new PrototypeJointPose.Limit(0, Math.toRadians(15));
    var pose =
        PrototypeJointPose.constrain(
            desired, rest, new Vec3(0, 1, 0), List.of(locked, locked, knee));
    double angle =
        Math.acos(
            Math.clamp(
                pose.getLast().subtract(pose.get(2)).normalize().dot(rest.getLast()), -1, 1));
    h.assertTrue(angle <= Math.toRadians(15) + 1e-7, "Adaptive reach exceeded saved knee limit");
    h.succeed();
  }
}
