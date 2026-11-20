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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.flipper.FlipperBlockEntity;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.flippers.SubLevelFlippers;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class FlipperTurningTests {
  @GameTest(template = "test_empty", batch = "flipper_turning", timeoutTicks = 320)
  public static void active_pair_turns_with_inertia_and_compensates_after_losing_a_flipper(
      GameTestHelper h) {
    var level = h.getLevel();
    var origin = new BlockPos(7552, 80, 128);
    for (int x = 470; x <= 474; x++) {
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
          var start = SubLevelPathfinder.position(body);
          h.runAfterDelay(
              40,
              () -> {
                var fin = (FlipperBlockEntity) level.getBlockEntity(p.west(2).south());
                var idle = fin.points(1);
                h.runAfterDelay(
                    20,
                    () -> {
                      h.assertTrue(!idle.equals(fin.points(1)), "Idle flippers stopped moving");
                      var now = SubLevelPathfinder.position(body);
                      h.assertTrue(
                          Math.hypot(now.x - start.x, now.z - start.z) < .1,
                          "Idle strokes requested unwanted horizontal propulsion");
                      h.assertTrue(
                          BrainMotorControls.move(
                              body, Vec3.ZERO, .6, 100, SubLevelPathfinder.Mode.WATER),
                          "Turn request rejected");
                      h.runAfterDelay(
                          60,
                          () -> {
                            var spin =
                                RigidBodyHandle.of(body)
                                    .getAngularVelocity(new org.joml.Vector3d())
                                    .y;
                            System.out.println(
                                "FLIPPER_RIGHT_TURN spin="
                                    + spin
                                    + " efforts="
                                    + SubLevelFlippers.getStrokeEfforts(body));
                            h.assertTrue(spin > .02, "Selected paddle pair produced no right yaw");
                            assertPair(h, body, p, null);
                            BrainMotorControls.stop(body);
                            h.runAfterDelay(
                                1,
                                () -> {
                                  double coast =
                                      RigidBodyHandle.of(body)
                                          .getAngularVelocity(new org.joml.Vector3d())
                                          .y;
                                  h.assertTrue(
                                      coast > 1e-4 && coast < spin + .1,
                                      "Turning momentum was reset or increased after stopping");
                                  h.assertTrue(
                                      BrainMotorControls.move(
                                          body, Vec3.ZERO, -.5, 100, SubLevelPathfinder.Mode.WATER),
                                      "Reverse turn rejected");
                                  h.runAfterDelay(
                                      65,
                                      () -> {
                                        double reversed =
                                            RigidBodyHandle.of(body)
                                                .getAngularVelocity(new org.joml.Vector3d())
                                                .y;
                                        System.out.println("FLIPPER_LEFT_TURN spin=" + reversed);
                                        h.assertTrue(
                                            reversed < -.02, "Paddle pair failed to reverse yaw");
                                        assertPair(h, body, p, null);
                                        var removed =
                                            SubLevelFlippers.getStrokeEfforts(body)
                                                .entrySet()
                                                .stream()
                                                .filter(
                                                    entry ->
                                                        entry.getKey().getX() < p.getX()
                                                            && Math.abs(entry.getValue()) > .001)
                                                .findFirst()
                                                .orElseThrow()
                                                .getKey();
                                        level.removeBlock(removed, false);
                                        h.runAfterDelay(
                                            8,
                                            () -> {
                                              h.assertTrue(
                                                  SubLevelFlippers.count(body) == 3,
                                                  "Lost flipper remained in layout");
                                              assertPair(h, body, p, removed);
                                              SubLevelContainer.getContainer(level)
                                                  .removeSubLevel(
                                                      body, SubLevelRemovalReason.REMOVED);
                                              h.succeed();
                                            });
                                      });
                                });
                          });
                    });
              });
        });
  }

  private static void assertPair(
      GameTestHelper h,
      dev.ryanhcode.sable.sublevel.ServerSubLevel body,
      BlockPos center,
      BlockPos removed) {
    var efforts = SubLevelFlippers.getStrokeEfforts(body);
    var powered =
        efforts.entrySet().stream().filter(entry -> Math.abs(entry.getValue()) > .001).toList();
    h.assertTrue(powered.size() == 2, "Turn powered all four flippers or none: " + efforts);
    h.assertTrue(
        powered.stream().filter(entry -> entry.getKey().getX() < center.getX()).count() == 1,
        "Turning pair did not include one paddle per side");
    h.assertTrue(
        removed == null || !efforts.containsKey(removed), "Compensation retained a removed paddle");
    h.assertTrue(
        efforts.values().stream().allMatch(value -> Double.isFinite(value) && Math.abs(value) <= 1),
        "Unbounded compensated stroke");
  }
}
