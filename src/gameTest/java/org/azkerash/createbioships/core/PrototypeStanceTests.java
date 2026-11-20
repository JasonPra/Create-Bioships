package org.azkerash.createbioships.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.locomotion.prototype.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class PrototypeStanceTests {
  @GameTest(template = "test_empty")
  public static void downhill_tuck_is_specific_to_friction_crab_front_legs(GameTestHelper h) {
    var hip = new Vec3(1, 2, 1);
    var original = new Vec3(2, 0, 2);
    var forward = new Vec3(0, 0, 1);
    var crab =
        new PrototypeGait.Leg(
            BlockPos.ZERO, hip, new Vec3(1, 0, 0), 2, 1, 1, 1, 0, LegMotionProfile.CRAB);
    var tucked = PrototypeStance.foot(crab, original, Vec3.ZERO, forward, -.5);
    h.assertTrue(
        tucked.x < original.x && tucked.z < original.z, "Front crab foot did not narrow and tuck");
    var anchored =
        new PrototypeGait.Leg(
            BlockPos.ZERO,
            hip,
            new Vec3(1, 0, 0),
            2,
            1,
            1,
            1,
            0,
            new LegMotionProfile(true, true, true, LegMotionProfile.GroundContact.ANCHORED));
    h.assertTrue(
        PrototypeStance.foot(anchored, original, Vec3.ZERO, forward, -.5).equals(original),
        "Crab stance leaked into anchored appendages");
    var generic = new PrototypeGait.Leg(BlockPos.ZERO, hip, new Vec3(1, 0, 0), 2, 1, 1, 1, 0);
    h.assertTrue(
        PrototypeStance.foot(generic, original, Vec3.ZERO, forward, -.5).equals(original),
        "Generic leg inherited crab behavior");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void support_plane_uses_anatomical_forward_and_caps_body_tilt(GameTestHelper h) {
    var points =
        List.of(new Vec3(-1, -2, -1), new Vec3(1, -2, -1), new Vec3(-1, 2, 1), new Vec3(1, 2, 1));
    var uphill = PrototypeStance.fit(points, new Vec3(0, 0, 1));
    var downhill = PrototypeStance.fit(points, new Vec3(0, 0, -1));
    h.assertTrue(
        uphill.valid() && Math.abs(uphill.grade() - 2) < 1e-7 && downhill.grade() < 0,
        "Slope did not follow facing");
    h.assertTrue(
        PrototypeStance.bodyUp(uphill).y >= Math.cos(Math.toRadians(25)) - 1e-7,
        "Body tilt exceeded limit");
    h.assertTrue(
        PrototypeStance.bodyUp(downhill).equals(new Vec3(0, 1, 0)),
        "Descending crab hull should remain level");
    h.succeed();
  }
}
