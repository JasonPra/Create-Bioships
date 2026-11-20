package org.azkerash.createbioships.core;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeLegKinematics;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class PrototypeLegKinematicsTests {
  @GameTest(template = "test_empty")
  public static void solver_preserves_segment_lengths_for_reachable_and_clamped_targets(
      GameTestHelper h) {
    for (var target :
        new Vec3[] {
          new Vec3(1, -1, 0), new Vec3(-3, -8, 5), Vec3.ZERO, new Vec3(.6, 0, 0), new Vec3(.6, 4, 0)
        }) {
      var points = PrototypeLegKinematics.solve(Vec3.ZERO, new Vec3(1, 0, 0), target, .6, .9, 1.2);
      double[] lengths = {.6, .9, 1.2};
      for (int i = 0; i < 3; i++) {
        h.assertTrue(
            Math.abs(points.get(i).distanceTo(points.get(i + 1)) - lengths[i]) < 1e-6,
            "IK changed segment " + i);
      }
      h.assertTrue(
          points.stream().allMatch(p -> Double.isFinite(p.x + p.y + p.z)),
          "IK produced a non-finite pose");
    }
    var target = new Vec3(1, -1, 0);
    var solved = PrototypeLegKinematics.solve(Vec3.ZERO, new Vec3(1, 0, 0), target, .6, .9, 1.2);
    h.assertTrue(
        solved.getLast().distanceTo(target) < 1e-6, "Reachable foot target was not reached");
    h.succeed();
  }
}
