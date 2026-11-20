package org.azkerash.createbioships.core;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.locomotion.prototype.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class PrototypeGaitTests {
  @GameTest(template = "test_empty")
  public static void planted_feet_release_removed_or_unreachable_support(GameTestHelper h) {
    var gait = new PrototypeGait();
    class Floor implements PrototypeGait.Terrain {
      private boolean present = true;

      public PrototypeGait.Contact probe(Vec3 desired, double height) {
        return present
            ? new PrototypeGait.Contact(new Vec3(desired.x, 0, desired.z), null, Vec3.ZERO)
            : null;
      }

      public Vec3 position(PrototypeGait.Contact contact) {
        return contact.point();
      }
    }
    var floor = new Floor();
    var leg =
        new PrototypeGait.Leg(
            BlockPos.ZERO, new Vec3(0, 1.5, 0), new Vec3(1, 0, 0), 1.5, .7, 1, 1.4, 0);
    gait.update(List.of(leg), Vec3.ZERO, 0, floor);
    h.assertTrue(gait.contacts() == 1, "Reachable ground was not planted");
    for (int tick = 1; tick < 10; tick++) {
      gait.update(List.of(leg), Vec3.ZERO, tick, floor);
    }
    h.assertTrue(gait.getProbeCount() == 1, "Stationary feet were cast every tick");
    floor.present = false;
    gait.update(List.of(leg), Vec3.ZERO, 12, floor);
    h.assertTrue(gait.contacts() == 0, "Removed terrain still supports body");
    floor.present = true;
    gait.update(List.of(leg), Vec3.ZERO, 20, floor);
    var distant =
        new PrototypeGait.Leg(BlockPos.ZERO, new Vec3(0, 10, 0), leg.outward(), 1.5, .7, 1, 1.4, 0);
    gait.update(List.of(distant), Vec3.ZERO, 21, floor);
    h.assertTrue(gait.contacts() == 0, "Unreachable cached contact supports hovering");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void body_motor_limits_acceleration_and_stops_supporting_in_air(GameTestHelper h) {
    var up = new Vec3(0, 1, 0);
    var request = new Vec3(6, 0, 0);
    var falling =
        PrototypeBodyMotor.calculate(Vec3.ZERO, Vec3.ZERO, up, request, 0, 0, .025, false);
    h.assertTrue(falling.velocity().equals(Vec3.ZERO), "Airborne motor cancels falling");
    var grounded =
        PrototypeBodyMotor.calculate(Vec3.ZERO, Vec3.ZERO, up, request, 0, 0, .025, true);
    h.assertTrue(Math.abs(grounded.velocity().x - .3) < 1e-9, "Motor ignores acceleration limit");
    h.assertTrue(
        grounded.velocity().y > 0 && grounded.angularVelocity().equals(Vec3.ZERO),
        "Flat support compensation is incorrect");
    h.succeed();
  }
}
