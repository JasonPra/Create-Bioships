package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.flippers.SubLevelFlippers;
import org.azkerash.createbioships.core.locomotion.SubLevelFacing;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;
import org.azkerash.createbioships.core.pathfinding.WaypointNavigator;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class AquaticWaypointTests {
  @GameTestGenerator
  public static List<TestFunction> directions() {
    var tests = new ArrayList<TestFunction>();
    for (var front : Direction.Plane.HORIZONTAL) {
      tests.add(
          new TestFunction(
              "aquatic_waypoints",
              "swimmer_follows_" + front.getName() + "_front",
              "create_bioships:test_empty",
              650,
              0,
              true,
              h -> physical(h, front)));
    }
    return tests;
  }

  private static void physical(GameTestHelper h, Direction front) {
    var level = h.getLevel();
    var origin = new BlockPos(7808 + front.get2DDataValue() * 64, 80, 128);
    for (int x = (origin.getX() - 16) >> 4; x <= (origin.getX() + 16) >> 4; x++) {
      for (int z = 6; z <= 10; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    // A clear water volume isolates paddle forces from wall and floor contacts.
    for (int x = -10; x <= 10; x++) {
      for (int z = -10; z <= 10; z++) {
        for (int y = -1; y <= 12; y++) {
          level.setBlock(
              origin.offset(x, y, z),
              (y == -1 ? Blocks.GLASS : Blocks.WATER).defaultBlockState(),
              Block.UPDATE_CLIENTS);
        }
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var core = origin.above(6);
          var cells = new LinkedHashMap<BlockPos, BlockState>();
          for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
              cells.put(
                  core.offset(x, 0, z), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState());
            }
          }
          cells.put(core, ComponentRegistry.CORE.get().defaultBlockState());
          cells.put(core.above(), ComponentRegistry.BRAIN.get().defaultBlockState());
          for (int side : new int[] {-1, 1}) {
            for (int z : new int[] {-1, 1}) {
              cells.put(
                  core.offset(side * 2, 0, z),
                  ComponentRegistry.FLIPPER
                      .get()
                      .defaultBlockState()
                      .setValue(
                          BlockStateProperties.HORIZONTAL_FACING,
                          side < 0 ? Direction.WEST : Direction.EAST)
                      .setValue(BlockStateProperties.WATERLOGGED, true));
            }
          }
          cells.forEach((cell, state) -> level.setBlock(cell, state, Block.UPDATE_ALL));
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  core,
                  List.copyOf(cells.keySet()),
                  new BoundingBox3i(core.offset(-2, 0, -1), core.offset(2, 1, 1)));
          var p = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, p);
          DevelopmentLifeSupport.pause(body);
          SubLevelFlippers.request(body);
          cells
              .keySet()
              .forEach(
                  cell ->
                      level.setBlock(cell, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS));
          SubLevelFacing.set(body, front);
          var handle = RigidBodyHandle.of(body);
          handle.teleport(
              new org.joml.Vector3d(body.logicalPose().position()),
              new org.joml.Quaterniond().rotationY(Math.PI / 3));
          dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).updatePose(body);
          h.runAfterDelay(
              40,
              () -> {
                // Exercise the engine boundary at a non-zero yaw, not just vector arithmetic.
                var before = handle.getLinearVelocity(new org.joml.Vector3d());
                var impulse = new org.joml.Vector3d(body.getMassTracker().getMass(), 0, 0);
                org.azkerash.createbioships.core.physics.SubLevelImpulse.applyWorld(
                    body, handle, impulse, new org.joml.Vector3d());
                var change = handle.getLinearVelocity(new org.joml.Vector3d()).sub(before);
                org.azkerash.createbioships.core.physics.SubLevelImpulse.applyWorld(
                    body, handle, impulse.negate(), new org.joml.Vector3d());
                h.assertTrue(
                    change.distance(new org.joml.Vector3d(1, 0, 0)) < 1e-5,
                    "World thrust was rotated again by Sable: " + change);
                var start = SubLevelPathfinder.position(body);
                var goal = start.add(6, 0, 0);
                var navigator = new WaypointNavigator();
                class Swim implements Runnable {
                  int ticks, next;

                  public void run() {
                    var progress =
                        navigator.follow(
                            body, List.of(goal), next, 1, 10, SubLevelPathfinder.Mode.WATER);
                    next = progress.next();
                    ticks += 5;
                    if (!progress.arrived() && ticks < 480) {
                      h.runAfterDelay(5, () -> run());
                      return;
                    }
                    double distance = SubLevelPathfinder.position(body).distanceTo(goal);
                    BrainMotorControls.stop(body);
                    SubLevelContainer.getContainer(level)
                        .removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    h.assertTrue(
                        progress.arrived() && distance < 1.5,
                        "Swimmer moved away or failed to reach waypoint for "
                            + front
                            + ": distance="
                            + distance);
                    h.succeed();
                  }
                }
                new Swim().run();
              });
        });
  }
}
