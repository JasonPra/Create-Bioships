package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.core.brain.behavior.WanderBehavior;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;
import org.azkerash.createbioships.core.pathfinding.*;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class WanderObstacleTests {
  private static void floor(GameTestHelper h, BlockPos origin) {
    var level = h.getLevel();
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, true);
      }
    }
    for (int x = -15; x <= 15; x++) {
      for (int z = -15; z <= 15; z++) {
        level.setBlock(
            origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
  }

  private static ServerSubLevel subject(GameTestHelper h, BlockPos origin) {
    var level = h.getLevel();
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        origin.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.above()),
            new BoundingBox3i(origin, origin.above()));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(body);
    SubLevelLocomotion.get(body);
    // Isolate navigation policy from a particular leg or swimming actuator.
    SubLevelData.locomotion(body, new SubLevelData.Locomotion(true, false, false));
    body.updateBoundingBox();
    return body;
  }

  private static ServerSubLevel wall(GameTestHelper h, BlockPos origin) {
    var cells = new ArrayList<BlockPos>();
    for (int y = 0; y < 5; y++) {
      for (int z = -2; z <= 2; z++) {
        var cell = origin.offset(0, y, z);
        h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        cells.add(cell);
      }
    }
    var result =
        SubLevelAssemblyHelper.assembleBlocks(
            h.getLevel(),
            origin,
            cells,
            new BoundingBox3i(origin.offset(0, 0, -2), origin.offset(0, 4, 2)));
    result.updateBoundingBox();
    return result;
  }

  @GameTest(template = "test_empty", batch = "foreign_route_obstacle", timeoutTicks = 100)
  public static void routes_detour_around_sable_collision_shapes_and_refresh_after_removal(
      GameTestHelper h) {
    var origin = new BlockPos(8704, 80, 128);
    floor(h, origin);
    h.runAfterDelay(
        20,
        () -> {
          var body = subject(h, origin);
          var obstacle = wall(h, origin.east(3));
          body.forceUpdateGlobalBounds();
          obstacle.forceUpdateGlobalBounds();
          var here = SubLevelPathfinder.position(body);
          var ownBox = SubLevelLocomotion.bounds(body).deflate(.01);
          h.assertTrue(
              SubLevelObstacles.capture(body, ownBox).clear(ownBox), "Creature blocked itself");
          h.assertTrue(
              !SubLevelPathfinder.movementClear(body, new Vec3(6, 0, 0), 0),
              "Live movement ignored a Sable wall");
          var route =
              SubLevelPathfinder.find(body, here.add(8, 0, 0), SubLevelPathfinder.Mode.GROUND);
          h.assertTrue(
              route.found()
                  && route.points().stream().anyMatch(point -> Math.abs(point.z - here.z) > 3),
              "Route did not detour around the foreign construct");
          var query = new AABB(origin.east(3)).inflate(.2);
          h.assertTrue(
              !SubLevelObstacles.capture(body, query).clear(query),
              "Sable wall had no collision snapshot");
          obstacle.logicalPose().position().add(0, 0, 12);
          obstacle.updateBoundingBox();
          h.assertTrue(
              SubLevelObstacles.capture(body, query).clear(query),
              "Moving construct left stale collision geometry");
          obstacle.logicalPose().position().add(0, 0, -12);
          obstacle.logicalPose().orientation().rotateY(Math.PI / 4);
          obstacle.updateBoundingBox();
          h.assertTrue(
              !SubLevelObstacles.capture(body, query).clear(query),
              "Rotated construct collision was not transformed into world space");
          SubLevelContainer.getContainer(h.getLevel())
              .removeSubLevel(obstacle, SubLevelRemovalReason.REMOVED);
          h.assertTrue(
              SubLevelObstacles.capture(body, query).clear(query),
              "Removed construct stayed cached as an obstacle");
          h.succeed();
        });
  }

  @GameTest(template = "test_empty", batch = "wander_obstacle_recovery", timeoutTicks = 120)
  public static void wandering_recovers_when_a_new_terrain_obstacle_blocks_its_route(
      GameTestHelper h) {
    recovery(h, false, new BlockPos(8832, 80, 128));
  }

  @GameTest(template = "test_empty", batch = "wander_sable_recovery", timeoutTicks = 120)
  public static void wandering_recovers_when_a_new_sable_obstacle_blocks_its_route(
      GameTestHelper h) {
    recovery(h, true, new BlockPos(8960, 80, 128));
  }

  private static void recovery(GameTestHelper h, boolean foreign, BlockPos origin) {
    floor(h, origin);
    final ServerSubLevel[] body = {null};
    var behavior = new WanderBehavior();
    h.runAfterDelay(
        20,
        () -> {
          body[0] = subject(h, origin);
          behavior.tick(body[0]);
        });
    h.runAfterDelay(
        65,
        () -> {
          var creature = body[0];
          h.getLevel().random.setSeed(1234);
          behavior.tick(creature);
          var previous = SubLevelPaths.get(creature);
          h.assertTrue(previous != null, "Wander did not select an initial route");
          var cell = BlockPos.containing(previous.points().get(previous.next()));
          if (foreign) {
            wall(h, cell).forceUpdateGlobalBounds();
          } else {
            for (int y = 0; y < 5; y++) {
              h.getLevel()
                  .setBlock(cell.above(y), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
          }
          behavior.tick(creature);
          behavior.tick(creature);
          var request = BrainMotorControls.current(creature);
          String phase =
              creature.getUserDataTag().getCompound(SubLevelData.BEHAVIOR).getString("phase");
          h.assertTrue(
              !phase.equals("paused"),
              "Blocked wander only paused instead of selecting an escape or route");
          h.assertTrue(
              request.velocity().lengthSqr() > 0 || Math.abs(request.yawRate()) > 0,
              "Recovery produced no movement or turn request");
          h.assertTrue(
              SubLevelPathfinder.movementClear(creature, request.velocity(), request.yawRate()),
              "Recovery drove into the obstacle");
          h.succeed();
        });
  }
}
