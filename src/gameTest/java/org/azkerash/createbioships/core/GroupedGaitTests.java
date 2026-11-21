package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.locomotion.SupportPolygon;
import org.azkerash.createbioships.core.locomotion.prototype.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class GroupedGaitTests {
  private static final PrototypeGait.Terrain FLOOR =
      new PrototypeGait.Terrain() {
        public PrototypeGait.Contact probe(Vec3 desired, double height) {
          return new PrototypeGait.Contact(new Vec3(desired.x, 0, desired.z), null, Vec3.ZERO);
        }

        public Vec3 position(PrototypeGait.Contact contact) {
          return contact.point();
        }
      };

  private static List<PrototypeGait.Leg> legs(int tick, int count) {
    var result = new ArrayList<PrototypeGait.Leg>();
    for (int side = 0; side < 2; side++) {
      for (int rank = 0; rank < 4; rank++) {
        int x = side == 0 ? -1 : 1;
        result.add(
            new PrototypeGait.Leg(
                new BlockPos(x, 0, rank),
                new Vec3(x, 1.5, rank - 1.5 + tick * .08),
                new Vec3(x, 0, 0),
                1.5,
                .7,
                1,
                1.4,
                side ^ rank % 2,
                LegMotionProfile.ARACHNID));
      }
    }
    return List.copyOf(result.subList(0, count));
  }

  @GameTest(template = "test_empty")
  public static void eight_ground_legs_transfer_groups_while_missing_leg_uses_careful_steps(
      GameTestHelper h) {
    for (int count : new int[] {8, 7}) {
      var gait = new PrototypeGait();
      int minimum = count;
      for (int tick = 0; tick < 200; tick++) {
        var legs = legs(tick, count);
        gait.update(legs, new Vec3(0, 0, 1.6), tick, FLOOR);
        minimum = Math.min(minimum, gait.contacts());
        h.assertTrue(gait.contacts() >= (count == 8 ? 4 : 6), "Gait lifted too many support feet");
      }
      h.assertTrue(gait.getStepCount() > 0, "Moving hips never advanced their feet");
      h.assertTrue(
          minimum == (count == 8 ? 4 : 6), "Gait did not select tetrapod/careful movement");
    }
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void support_boundary_uses_contacts_and_recovery_rejects_obstructed_poses(
      GameTestHelper h) {
    var contacts =
        List.of(
            new Vec3(-2, 1, -2),
            new Vec3(2, 0, -2),
            new Vec3(2, 3, 2),
            new Vec3(-2, 0, 2),
            Vec3.ZERO);
    h.assertTrue(SupportPolygon.containsProjection(Vec3.ZERO, contacts), "Balanced body rejected");
    h.assertTrue(
        !SupportPolygon.containsProjection(new Vec3(3, 0, 0), contacts),
        "Unsupported body accepted");
    var leg =
        new PrototypeGait.Leg(
            BlockPos.ZERO,
            new Vec3(0, 3, 0),
            new Vec3(1, 0, 0),
            3,
            1,
            2,
            2,
            0,
            LegMotionProfile.ARACHNID);
    class Terrain implements PrototypeGait.Terrain {
      private int casts;
      private boolean clear;

      public PrototypeGait.Contact probe(Vec3 desired, double height) {
        casts++;
        return new PrototypeGait.Contact(new Vec3(desired.x, 1, desired.z), null, Vec3.ZERO);
      }

      public Vec3 position(PrototypeGait.Contact contact) {
        return contact.point();
      }

      public boolean clearPose(PrototypeGait.Leg limb, List<Vec3> pose) {
        return clear;
      }
    }
    var terrain = new Terrain();
    var foot = new Vec3(3, 0, 0);
    h.assertTrue(
        FootholdRecovery.nearest(leg, foot, foot, terrain) == null,
        "Recovery accepted an obstructed swing");
    h.assertTrue(terrain.casts <= 9, "Emergency search exceeded its probe budget");
    terrain.clear = true;
    h.assertTrue(
        FootholdRecovery.nearest(leg, foot, foot, terrain) != null,
        "Recovery failed to find reachable higher ground");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void one_or_two_adhesive_toes_never_qualify_for_ground_suspension(
      GameTestHelper h) {
    for (int count : new int[] {1, 2}) {
      var gait = new PrototypeGait();
      var legs = legs(0, count);
      gait.update(legs, Vec3.ZERO, 0, FLOOR);
      h.assertTrue(
          gait.requiredContacts(legs) >= 3 && gait.contacts() < gait.requiredContacts(legs),
          "One/two toes qualified for the ground-motor fallback");
    }
    var pose =
        org.azkerash.createbioships.compat.letitwalk.CompactLegGeometry.restPoints(
            BlockPos.ZERO, net.minecraft.core.Direction.EAST, 4, 0, true);
    double reach = pose.getLast().x - pose.getFirst().x;
    double drop = pose.getFirst().y - pose.getLast().y;
    h.assertTrue(reach > drop * 3, "Distal huntsman leg remained a vertical stilt");
    h.assertTrue(
        Math.abs(pose.getLast().z - pose.getFirst().z) < 1e-8,
        "Anatomy revision changed the zero-degree rest angle");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void support_positions_must_surround_mass_on_ground_wall_and_ceiling(
      GameTestHelper h) {
    for (var normal : List.of(new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 0, 0))) {
      var side = normal.x == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
      var along = side.cross(normal);
      var balanced =
          List.of(
              side.scale(-2).add(along.scale(-2)),
              side.scale(2).add(along.scale(-2)),
              side.scale(2).add(along.scale(2)),
              side.scale(-2).add(along.scale(2)));
      h.assertTrue(
          SupportPolygon.containsProjection(Vec3.ZERO, balanced, normal),
          "Balanced contacts rejected in support plane");
      var triangle =
          List.of(side.scale(-3).subtract(along), side.scale(3).subtract(along), along.scale(3));
      h.assertTrue(
          SupportPolygon.containsProjection(Vec3.ZERO, triangle, normal),
          "Three well-positioned toes could not support a centered body");
      h.assertTrue(
          !SupportPolygon.containsProjection(side.scale(4), balanced, normal),
          "Moving the center of mass outside planted feet retained support");
      h.assertTrue(
          !SupportPolygon.containsProjection(
              Vec3.ZERO, List.of(side.scale(-2), Vec3.ZERO, side.scale(2)), normal),
          "Collinear toes formed a fictitious support area");
      var sameSide = balanced.stream().map(point -> point.add(side.scale(4))).toList();
      h.assertTrue(
          !SupportPolygon.containsProjection(Vec3.ZERO, sameSide, normal),
          "Several same-side toes counted as balanced support");
    }
    h.succeed();
  }
}
