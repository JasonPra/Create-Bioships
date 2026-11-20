package org.azkerash.createbioships.core;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.compat.letitwalk.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CompactLegSocketBlock;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class LegRestGeometryTests {
  @GameTest(template = "test_empty", batch = "neutral_leg_geometry")
  public static void socket_zero_is_neutral_and_manual_angles_are_absolute_offsets(
      GameTestHelper h) {
    // Move sockets fore/aft and to either side: position must never inject an automatic fan.
    for (var socket : List.of(new BlockPos(-2, 0, -3), new BlockPos(2, 0, 3))) {
      for (var face : Direction.Plane.HORIZONTAL) {
        var normal = Vec3.atLowerCornerOf(face.getNormal());
        for (int angle : new int[] {-45, -15, 0, 15, 45}) {
          var points = CompactLegGeometry.restPoints(socket, face, 2, angle, true);
          var span = points.getLast().subtract(points.getFirst());
          var direction = new Vec3(span.x, 0, span.z).normalize();
          var expected = normal.yRot((float) Math.toRadians(angle)).normalize();
          h.assertTrue(
              direction.distanceTo(expected) < 1e-6,
              "Socket position changed the requested rest angle: " + face + "/" + angle);
        }
      }
    }
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "extended_leg_geometry")
  public static void all_compact_leg_types_accept_six_and_scale_their_physical_geometry(
      GameTestHelper h) {
    for (var socket :
        List.of(ComponentRegistry.CRAB_LEG.get(), ComponentRegistry.ARACHNID_LEG.get())) {
      var state = socket.defaultBlockState().setValue(CompactLegSocketBlock.HEIGHT, 6);
      h.assertTrue(
          state.getValue(CompactLegSocketBlock.HEIGHT) == 6, "Leg socket rejected added extension");
    }
    for (boolean spider : new boolean[] {false, true}) {
      var shortLeg = CompactLegGeometry.restPoints(BlockPos.ZERO, Direction.EAST, 3, 0, spider);
      var longLeg = CompactLegGeometry.restPoints(BlockPos.ZERO, Direction.EAST, 6, 0, spider);
      double expectedScale = 6.5 / 3.5;
      for (int i = 0; i < 3; i++) {
        h.assertTrue(
            Math.abs(
                    longLeg.get(i + 1).distanceTo(longLeg.get(i))
                            / shortLeg.get(i + 1).distanceTo(shortLeg.get(i))
                        - expectedScale)
                < 1e-6,
            "Long leg retained a short bone");
      }
      h.assertTrue(
          CompactLegCollisions.width(1, 6, spider) > CompactLegCollisions.width(1, 3, spider),
          "Long limb collision width was still clamped to three");
    }
    h.assertTrue(
        new CrabLegKinematics(6).standingHeight() == 6.5, "Kinematics clamped the new extension");
    h.assertTrue(
        ComponentRegistry.COMPACT_LIMB
                .get()
                .defaultBlockState()
                .setValue(
                    org.azkerash.createbioships.components.logic.legs.CompactLimbBlock.HEIGHT, 6)
                .getValue(org.azkerash.createbioships.components.logic.legs.CompactLimbBlock.HEIGHT)
            == 6,
        "Generated limb collision state rejected six");
    h.succeed();
  }
}
