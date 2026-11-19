package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.brain.*;
import org.Azkerash.create_bioships.core.brain.behavior.*;
import org.Azkerash.create_bioships.core.legs.SubLevelLegs;
import org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion;
import org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;
import org.Azkerash.create_bioships.development.testing.LocomotionFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class WanderTests {
  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "water_route_volume")
  public static void underwater_world_routes_include_vertical_motion(GameTestHelper helper) {
    var level = helper.getLevel();
    var base = new BlockPos(3072, 86, 128);
    for (int cx = 191; cx <= 193; cx++)
      for (int cz = 7; cz <= 9; cz++) level.setChunkForced(cx, cz, true);
    for (int x = -5; x <= 5; x++)
      for (int y = -4; y <= 4; y++)
        for (int z = -5; z <= 5; z++)
          level.setBlock(base.offset(x, y, z), Blocks.WATER.defaultBlockState(), 3);
    helper.runAfterDelay(
        20,
        () -> {
          level.setBlock(base, ComponentRegistry.CORE.get().defaultBlockState(), 3);
          level.setBlock(base.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level, base, List.of(base, base.above()), new BoundingBox3i(base, base.above()));
          CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
          DevelopmentLifeSupport.pause(body);
          var actual = SubLevelLocomotion.get(body);
          var destination = SubLevelPathfinder.position(body).add(2, 2, 2);
          helper.assertTrue(
              !SubLevelPathfinder.find(body, destination, SubLevelPathfinder.Mode.WATER).found(),
              "A creature without fins received an underwater route");
          // Exercise the future appendage capability contract without introducing a fin block.
          SubLevelData.locomotion(body, new SubLevelData.Locomotion(false, true, false));
          var route = SubLevelPathfinder.find(body, destination, SubLevelPathfinder.Mode.WATER);
          SubLevelData.locomotion(body, actual);
          helper.assertTrue(
              route.found() && route.points().stream().anyMatch(p -> p.y > base.getY() + 1),
              "World-water adapter failed to plan a vertical route");
          helper.succeed();
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 20, batch = "shared_routes")
  public static void bounded_ground_detour_steps_and_three_dimensional_water_routes(
      GameTestHelper helper) {
    SubLevelPathfinder.Space ground =
        (p, mode) ->
            mode == SubLevelPathfinder.Mode.GROUND
                && p.getY() == 0
                && p.getX() >= 0
                && p.getX() <= 6
                && p.getZ() >= 0
                && p.getZ() <= 5
                && !(p.getX() == 2 && p.getZ() < 4);
    var land =
        SubLevelPathfinder.search(
            ground, BlockPos.ZERO, new BlockPos(5, 0, 0), SubLevelPathfinder.Mode.GROUND, 256);
    helper.assertTrue(
        !land.isEmpty() && land.stream().anyMatch(p -> p.getZ() >= 4),
        "Ground route failed to detour around the wall");
    helper.assertTrue(
        land.stream().allMatch(p -> ground.passable(p, SubLevelPathfinder.Mode.GROUND)),
        "Ground path crossed a blocked cell");
    SubLevelPathfinder.Space steps =
        (p, m) ->
            p.getZ() == 0 && p.getX() >= 0 && p.getX() <= 4 && p.getY() == (p.getX() < 2 ? 0 : 1);
    var stair =
        SubLevelPathfinder.search(
            steps, BlockPos.ZERO, new BlockPos(4, 1, 0), SubLevelPathfinder.Mode.GROUND, 64);
    helper.assertTrue(
        !stair.isEmpty() && stair.getLast().getY() == 1,
        "Ground route could not take a one-block step");
    SubLevelPathfinder.Space water =
        (p, m) ->
            m == SubLevelPathfinder.Mode.WATER
                && p.getX() >= 0
                && p.getX() <= 4
                && p.getY() >= 0
                && p.getY() <= 4
                && p.getZ() == 0
                && (p.getY() != 1 || p.getX() == 3);
    var swim =
        SubLevelPathfinder.search(
            water, BlockPos.ZERO, new BlockPos(0, 3, 0), SubLevelPathfinder.Mode.WATER, 128);
    helper.assertTrue(
        !swim.isEmpty() && swim.stream().anyMatch(p -> p.getX() == 3 && p.getY() == 1),
        "Water route did not navigate vertically through the opening");
    helper.assertTrue(
        swim.stream().allMatch(p -> water.passable(p, SubLevelPathfinder.Mode.WATER)),
        "Water route left its passable volume");
    helper.assertTrue(
        SubLevelPathfinder.search(
                ground, BlockPos.ZERO, new BlockPos(5, 0, 0), SubLevelPathfinder.Mode.GROUND, 1)
            .isEmpty(),
        "Planner ignored its expansion budget");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 360, batch = "wander_brain")
  public static void grid_flags_factory_card_movement_and_removal(GameTestHelper helper) {
    var level = helper.getLevel();
    var base = new BlockPos(2816, 80, 128);
    for (int cx = 175; cx <= 177; cx++)
      for (int cz = 7; cz <= 9; cz++) level.setChunkForced(cx, cz, true);
    for (int x = -16; x <= 16; x++)
      for (int z = -16; z <= 22; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        20,
        () -> {
          var cells = LocomotionFixture.place(level, base);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  base.offset(0, 2, 0),
                  cells,
                  new BoundingBox3i(base.offset(-2, 2, -1), base.offset(2, 3, 1)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          DevelopmentLifeSupport.pause(body);
          SubLevelLegs.request(body);
          var flags = SubLevelLocomotion.get(body);
          helper.assertTrue(
              flags.hasLegs() && !flags.hasFins() && !flags.hasWallClimbers(),
              "Grid appendage flags were incorrect");
          helper.assertTrue(
              !SubLevelPathfinder.allowed(body, SubLevelPathfinder.Mode.WATER),
              "Crab legs unlocked swimming routes");
          var brain = SubLevelBrains.get(body);
          BrainBehaviors.advance(body);
          helper.assertTrue(BrainBehaviors.active(body) == null, "A blank brain ran a behavior");
          helper.assertTrue(
              !BrainMotorControls.move(
                  body, new Vec3(0, 1, 0), 0, 20, SubLevelPathfinder.Mode.WATER),
              "Ground legs accepted a swimming request");
          SubLevelData.locomotion(body, new SubLevelData.Locomotion(true, true, false));
          helper.assertTrue(
              BrainMotorControls.move(body, new Vec3(0, 1, 0), 0, 20, SubLevelPathfinder.Mode.WATER)
                  && BrainMotorControls.current(body).velocity().y == 1,
              "Swimming control surface discarded vertical motion");
          BrainMotorControls.stop(body);
          SubLevelData.locomotion(body, flags);
          var card = ComponentRegistry.WANDER_BEHAVIOR_CARD.get().getDefaultInstance();
          helper.assertTrue(
              brain.inventory().canPlaceItem(0, card), "Brain rejected the factory card tag");
          brain.inventory().setItem(0, card);
          BrainBehaviors.advance(body);
          helper.assertTrue(
              WanderBehavior.ID.equals(BrainBehaviors.active(body)),
              "Wander did not activate from the grid inventory");
          var initial = SubLevelPathfinder.position(body);
          boolean[] sawPath = {false};
          for (int delay = 50; delay < 220; delay += 10)
            helper.runAfterDelay(
                delay,
                () -> {
                  var path = org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.get(body);
                  if (path != null) {
                    sawPath[0] = true;
                    helper.assertTrue(
                        path.next() < path.points().size(), "Active path has invalid waypoint");
                  }
                });
          helper.runAfterDelay(
              220,
              () -> {
                helper.assertTrue(sawPath[0], "Wander never exposed its actual active path");
                var end = SubLevelPathfinder.position(body);
                var state = body.getUserDataTag().getCompound(SubLevelData.BEHAVIOR);
                System.out.println(
                    "WANDER_RUNTIME delta="
                        + end.subtract(initial)
                        + " state="
                        + state
                        + " flags="
                        + SubLevelData.locomotion(body));
                helper.assertTrue(
                    Math.hypot(end.x - initial.x, end.z - initial.z) > .8,
                    "Wander did not move the physical creature");
                var appendageBounds = SubLevelLocomotion.bounds(body);
                var hull = body.boundingBox();
                helper.assertTrue(
                    appendageBounds.getXsize() > hull.maxX() - hull.minX() + .5
                        || appendageBounds.getZsize() > hull.maxZ() - hull.minZ() + .5,
                    "Shared navigation bounds omitted the physical appendages");
                brain.inventory().setItem(0, ItemStack.EMPTY);
                BrainBehaviors.advance(body);
                helper.assertTrue(
                    BrainBehaviors.active(body) == null
                        && BrainMotorControls.current(body).expires() == 0,
                    "Removing Wander left its movement request active");
                helper.assertTrue(
                    org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.get(body) == null,
                    "Removing Wander left a stale path overlay");
                for (var pose : SubLevelLegs.poses(body)) level.removeBlock(pose.cell(), false);
                helper.assertTrue(
                    !SubLevelLocomotion.get(body).hasLegs(),
                    "Removing the last appendage left hasLegs true");
                helper.assertTrue(
                    !SubLevelPathfinder.allowed(body, SubLevelPathfinder.Mode.GROUND),
                    "Ground navigation ignored the appendage flag");
                helper.succeed();
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 120, batch = "wander_exhibit")
  public static void locomotion_exhibit_starts_with_wander_and_resets(GameTestHelper helper) {
    var level = helper.getLevel();
    LocomotionFixture.reset(level);
    helper.runAfterDelay(
        10,
        () -> {
          helper.assertTrue(LocomotionFixture.start(level) == 1, "Locomotion START failed");
          var body = LocomotionFixture.body(level);
          var brain = SubLevelBrains.get(body);
          helper.assertTrue(
              brain.inventory().getItem(0).is(ComponentRegistry.WANDER_BEHAVIOR_CARD.get()),
              "Exhibit brain did not contain Wander");
          helper.assertTrue(
              SubLevelData.hungerState(body).paused(), "Exhibit hunger was not paused");
          for (var cell : SubLevelHealth.blocks(body))
            if (level.getBlockState(cell).is(ComponentRegistry.CRAB_LEG.get())) {
              var tuning =
                  org.Azkerash.create_bioships.compat.letitwalk.LimbTuning.get(level, cell);
              helper.assertTrue(
                  tuning.equals(org.Azkerash.create_bioships.compat.letitwalk.LimbTuning.DEFAULT),
                  "Native exhibit unexpectedly overrides default articulation tuning");
            }
          helper.runAfterDelay(
              20,
              () -> {
                LocomotionFixture.reset(level);
                helper.assertTrue(
                    LocomotionFixture.body(level) == null,
                    "Locomotion RESET left the assembled subject behind");
                helper.succeed();
              });
        });
  }
}
