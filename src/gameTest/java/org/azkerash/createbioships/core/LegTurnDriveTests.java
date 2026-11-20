package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.locomotion.LegTurnDrive;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeGait;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class LegTurnDriveTests {
  @GameTest(template = "test_empty")
  public static void planted_foot_couple_turns_without_sideways_force_or_airborne_torque(
      GameTestHelper h) {
    var feet =
        List.of(
            new LegTurnDrive.Foot(new Vec3(-1, 0, -2), null),
            new LegTurnDrive.Foot(new Vec3(1, 0, 2), null));
    var impulses = LegTurnDrive.impulses(feet, new Vec3(0, 1, 0), 10, 20, 0, 1, .05);
    var net = impulses.get(0).add(impulses.get(1));
    double torque =
        feet.get(0).point().cross(impulses.get(0)).y + feet.get(1).point().cross(impulses.get(1)).y;
    h.assertTrue(
        net.length() < 1e-9 && torque > 0 && torque <= 20 * .6 * .05 + 1e-9,
        "Foot couple introduced translation or unbounded spin");
    var excessive = LegTurnDrive.impulses(feet, new Vec3(0, 1, 0), 10, 20, 0, 100, .05);
    h.assertTrue(excessive.equals(impulses), "Huge yaw request bypassed turn limit");
    var unsupported =
        LegTurnDrive.impulses(List.of(feet.getFirst()), new Vec3(0, 1, 0), 10, 20, 0, 1, .05);
    h.assertTrue(
        unsupported.getFirst().equals(Vec3.ZERO), "One foot manufactured a balanced turn couple");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void turning_requests_replant_feet_before_the_hull_rotates(GameTestHelper h) {
    var gait = new PrototypeGait();
    var floor =
        new PrototypeGait.Terrain() {
          public PrototypeGait.Contact probe(Vec3 desired, double height) {
            return new PrototypeGait.Contact(new Vec3(desired.x, 0, desired.z), null, Vec3.ZERO);
          }

          public Vec3 position(PrototypeGait.Contact contact) {
            return contact.point();
          }
        };
    var legs = new ArrayList<PrototypeGait.Leg>();
    for (int x : new int[] {-1, 1}) {
      for (int z : new int[] {-1, 1}) {
        legs.add(
            new PrototypeGait.Leg(
                new BlockPos(x, 0, z),
                new Vec3(x, 1.5, z),
                new Vec3(x, 0, 0),
                1.5,
                .7,
                1,
                1.4,
                x == z ? 0 : 1));
      }
    }
    gait.update(legs, Vec3.ZERO, 0, floor, new Vec3(0, 0, 1), 0);
    gait.update(legs, Vec3.ZERO, 1, floor, new Vec3(0, 0, 1), 1);
    h.assertTrue(gait.getStepCount() > 0, "Turn started with no anticipatory foot placement");
    h.assertTrue(gait.contacts() >= 2, "Turn lifted all supporting feet");
    h.succeed();
  }
}
