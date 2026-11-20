package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.pathfinding.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class WaypointNavigationTests {
  private static final class World implements WaypointNavigator.Environment {
    private Vec3 position = Vec3.ZERO, forward = new Vec3(0, 0, 1), velocity;
    private double yaw;
    private long tick;
    private int plans;
    private boolean legs = true, completelyBlocked;
    private List<Vec3> planned = List.of(new Vec3(2, 0, 0), new Vec3(2, 0, 6), new Vec3(0, 0, 6));

    public Vec3 position() {
      return position;
    }

    public Vec3 forward() {
      return forward;
    }

    public long tick() {
      return tick;
    }

    public boolean canStrafe() {
      return legs;
    }

    public boolean move(Vec3 movement, double turn) {
      if (completelyBlocked || (position.x < 1.5 && position.z < 2 && movement.z > .01)) {
        return false;
      }
      velocity = movement;
      yaw = turn;
      return true;
    }

    public List<Vec3> plan(Vec3 goal, SubLevelPathfinder.Mode mode) {
      plans++;
      return planned;
    }

    public void publish(List<Vec3> points, int next, SubLevelPathfinder.Mode mode) {}
  }

  @GameTest(template = "test_empty")
  public static void legged_detour_strafes_then_rejoins_unchanged_main_route(GameTestHelper h) {
    var world = new World();
    var navigator = new WaypointNavigator();
    var primary = List.of(new Vec3(0, 0, 6));
    var first = navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(
        first.detouring() && first.accepted(), "Obstacle did not create a secondary route");
    h.assertTrue(
        world.velocity.x > 0 && world.velocity.z == 0 && world.yaw == 0,
        "Legs did not strafe while keeping their nose toward the main target");
    h.assertTrue(primary.equals(List.of(new Vec3(0, 0, 6))), "Detour rewrote main destination");
    world.position = new Vec3(2, 0, 0);
    world.tick++;
    navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(
        world.velocity.z > 0 && world.yaw < 0, "Walk-around segment lost main-target facing");
    world.position = new Vec3(2, 0, 6);
    world.tick++;
    navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(world.velocity.x < 0, "Detour did not return toward main route");
    world.position = primary.getFirst();
    world.tick++;
    var last = navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(
        last.arrived() && last.next() == 1 && navigator.getDetourWaypoints().isEmpty(),
        "Detour never rejoined main route");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void flipper_detour_turns_before_swimming_without_lateral_motion(GameTestHelper h) {
    var world = new World();
    world.legs = false;
    var navigator = new WaypointNavigator();
    var primary = List.of(new Vec3(0, 0, 6));
    var result = navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.WATER);
    h.assertTrue(result.detouring() && result.accepted(), "Swimmer did not acquire detour");
    h.assertTrue(
        world.velocity.x == 0 && world.yaw > 0, "Flippers slid sideways instead of turning");
    world.forward = new Vec3(1, 0, 0);
    world.tick++;
    navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.WATER);
    h.assertTrue(
        world.velocity.x > 0 && world.velocity.z == 0, "Swimmer failed nose-aligned detour travel");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void blocked_detour_planning_is_rate_limited(GameTestHelper h) {
    var world = new World();
    world.completelyBlocked = true;
    world.planned = List.of();
    var navigator = new WaypointNavigator();
    var route = List.of(new Vec3(0, 0, 6));
    navigator.follow(world, route, 0, 2, SubLevelPathfinder.Mode.GROUND);
    world.tick = 1;
    navigator.follow(world, route, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(world.plans == 1, "Blocked creature replanned every update");
    world.tick = 20;
    navigator.follow(world, route, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(world.plans == 2, "Blocked creature never retried planning");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void waypoint_pruning_skips_open_cells_but_keeps_obstacle_and_gap_bends(
      GameTestHelper h) {
    var straight = new ArrayList<BlockPos>();
    for (int x = 1; x <= 8; x++) {
      straight.add(new BlockPos(x, 0, 0));
    }
    SubLevelPathfinder.Space open = (cell, mode) -> true;
    h.assertTrue(
        SubLevelPathfinder.waypoints(open, BlockPos.ZERO, straight, SubLevelPathfinder.Mode.GROUND)
            .equals(List.of(new BlockPos(8, 0, 0))),
        "Open corridor retained one target per block");
    var around =
        List.of(
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, 2),
            new BlockPos(1, 0, 2),
            new BlockPos(2, 0, 2),
            new BlockPos(3, 0, 2),
            new BlockPos(4, 0, 2),
            new BlockPos(4, 0, 1),
            new BlockPos(4, 0, 0));
    SubLevelPathfinder.Space wall = (cell, mode) -> !(cell.getX() == 2 && cell.getZ() < 2);
    var bends =
        SubLevelPathfinder.waypoints(wall, BlockPos.ZERO, around, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(
        bends.size() > 1 && bends.getLast().equals(new BlockPos(4, 0, 0)),
        "Pruning cut through an obstacle");
    SubLevelPathfinder.Space gap = (cell, mode) -> !(cell.getX() == 1 && cell.getZ() == 0);
    h.assertTrue(
        SubLevelPathfinder.waypoints(gap, BlockPos.ZERO, around, SubLevelPathfinder.Mode.GROUND)
                .size()
            > 1,
        "Pruning skipped unsupported/unloaded terrain");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void contact_escape_is_a_secondary_waypoint_without_skipping_the_main_target(
      GameTestHelper h) {
    var world = new World();
    world.planned = List.of();
    var navigator = new WaypointNavigator();
    var primary = List.of(new Vec3(0, 0, 6));
    var first = navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(
        first.accepted() && first.detouring() && navigator.getDetourWaypoints().size() == 1,
        "Blocked start did not create a bounded sidestep waypoint");
    world.position = navigator.getDetourWaypoints().getFirst();
    world.tick = 10;
    var resumed = navigator.follow(world, primary, 0, 2, SubLevelPathfinder.Mode.GROUND);
    h.assertTrue(
        !resumed.arrived() && resumed.next() == 0 && world.velocity.z > 0,
        "Completing a sidestep incorrectly completed the main route");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void search_includes_goal_elevation_beyond_the_old_eight_block_band(
      GameTestHelper h) {
    SubLevelPathfinder.Space stairs = (cell, mode) -> true;
    var goal = new BlockPos(0, -10, 10);
    var path =
        SubLevelPathfinder.search(stairs, BlockPos.ZERO, goal, SubLevelPathfinder.Mode.GROUND, 512);
    h.assertTrue(
        !path.isEmpty() && path.getLast().equals(goal),
        "Valid descent was rejected by a fixed vertical search band");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void terrain_speed_slows_before_climbs_and_descents_and_recovers_gradually(
      GameTestHelper h) {
    h.assertTrue(TerrainSpeedControl.factor(0, 1) == 1, "Level terrain slowed travel");
    double uphill = TerrainSpeedControl.factor(1, 1);
    double downhill = TerrainSpeedControl.factor(-1, 1);
    h.assertTrue(
        uphill < 1 && downhill < uphill && downhill >= .25,
        "Slope speed policy did not brake more strongly downhill");
    double released = TerrainSpeedControl.recover(downhill, 1, .1);
    h.assertTrue(
        released > downhill && released < 1, "Speed cap disappeared instantly on level ground");
    h.assertTrue(
        TerrainSpeedControl.recover(1, downhill, .1) == downhill, "Slope braking was delayed");
    h.succeed();
  }
}
