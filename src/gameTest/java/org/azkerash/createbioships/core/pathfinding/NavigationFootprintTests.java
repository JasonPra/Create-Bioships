package org.azkerash.createbioships.core.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;
import org.azkerash.createbioships.development.testing.HuntsmanFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class NavigationFootprintTests {
  @GameTest(template = "test_empty", timeoutTicks = 100)
  public static void full_leg_geometry_blocks_obstacles_but_empty_inter_leg_space_does_not(
      GameTestHelper h) {
    var body = HuntsmanFixture.spawn(h.getLevel(), h.absolutePos(new BlockPos(0, 50, 0)));
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(
              SubLevelLegs.segments(body).size() == 24, "Missing physical huntsman segments");
          var geometry = new NavigationFootprint(body);
          var pose = SubLevelLegs.poses(body).getFirst();
          var endpoints = pose.collisionEndpoints();
          var midpoint =
              body.logicalPose()
                  .transformPosition(endpoints.get(2).add(endpoints.get(3)).scale(.5));
          h.assertTrue(
              geometry.intersects(
                  AABB.ofSize(midpoint, .1, .1, .1), Vec3.ZERO, Double.NEGATIVE_INFINITY, true),
              "Navigation discarded a real leg collider");
          var bounds = SubLevelLocomotion.bounds(body);
          boolean gap = false;
          for (double x = bounds.minX + .5; x < bounds.maxX && !gap; x++) {
            for (double z = bounds.minZ + .5; z < bounds.maxZ; z++) {
              var candidate = AABB.ofSize(new Vec3(x, bounds.minY + 1, z), .2, .2, .2);
              if (!geometry.intersects(candidate, Vec3.ZERO, Double.NEGATIVE_INFINITY, true)) {
                gap = true;
                break;
              }
            }
          }
          h.assertTrue(gap, "Empty inter-leg space remained a fictitious solid navigation wall");
          h.succeed();
        });
  }
}
