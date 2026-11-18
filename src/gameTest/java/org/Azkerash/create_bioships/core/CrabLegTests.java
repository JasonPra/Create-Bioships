package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.legs.SubLevelLegs;
import org.Azkerash.create_bioships.development.testing.LocomotionFixture;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CrabLegTests {
  @GameTest(template = "test_empty", timeoutTicks = 160, batch = "compact_ragdoll")
  public static void one_uncommanded_leg_falls_and_ragdolls(GameTestHelper helper) {
    var level = helper.getLevel();
    var base = new BlockPos(2560, 80, 128);
    for (int cx = 159; cx <= 161; cx++)
      for (int cz = 7; cz <= 9; cz++) level.setChunkForced(cx, cz, true);
    for (int x = -8; x <= 8; x++)
      for (int z = -8; z <= 8; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        20,
        () -> {
          var core = base.above(8);
          var armor = core.east();
          var leg = core.east(2);
          level.setBlock(
              core,
              org.Azkerash.create_bioships
                  .components
                  .ComponentRegistry
                  .CORE
                  .get()
                  .defaultBlockState(),
              3);
          level.setBlock(
              armor,
              org.Azkerash.create_bioships
                  .components
                  .ComponentRegistry
                  .CHITIN_ARMOR
                  .get()
                  .defaultBlockState(),
              3);
          level.setBlock(
              leg,
              org.Azkerash.create_bioships
                  .components
                  .ComponentRegistry
                  .CRAB_LEG
                  .get()
                  .defaultBlockState()
                  .setValue(
                      net.minecraft.world.level.block.state.properties.BlockStateProperties
                          .HORIZONTAL_FACING,
                      Direction.EAST),
              3);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level, core, List.of(core, armor, leg), new BoundingBox3i(core, leg));
          var logical = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, logical);
          SubLevelData.hungerPaused(body, true);
          SubLevelLegs.request(body);
          var initial = body.logicalPose().transformPosition(Vec3.atCenterOf(logical));
          helper.runAfterDelay(
              20,
              () -> {
                var falling = body.logicalPose().transformPosition(Vec3.atCenterOf(logical));
                helper.assertTrue(falling.y < initial.y - 1, "An airborne leg held its body up");
                helper.runAfterDelay(
                    65,
                    () -> {
                      var landed = body.logicalPose().transformPosition(Vec3.atCenterOf(logical));
                      helper.assertTrue(
                          landed.y < base.getY() + 2.5,
                          "One uncommanded leg hovered above the floor");
                      var pose = SubLevelLegs.poses(body).getFirst();
                      helper.assertTrue(
                          pose.collisionEndpoints().getLast().distanceTo(pose.joints().getLast())
                              > .15,
                          "Uncommanded physical leg remained rigid in its animation pose");
                      helper.succeed();
                    });
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 160, batch = "native_box_terrain")
  public static void native_segment_middle_collides_with_existing_obstacle(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = new BlockPos(2432, 80, 128);
    for (int x = 151; x <= 153; x++) for (int z = 7; z <= 9; z++) level.setChunkForced(x, z, true);
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= 3; z++)
        level.setBlock(pos.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
    level.setBlock(pos.above(), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        20,
        () -> {
          var pose = new dev.ryanhcode.sable.companion.math.Pose3d();
          pose.position().set(pos.getX() + .5, pos.getY() + 5, pos.getZ() + .5);
          pose.orientation().rotationZ(Math.PI / 2);
          var box =
              new dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject(
                  pose, new org.joml.Vector3d(.15, 1.5, .15), .25) {
                @Override
                public void getBoundingBox(
                    dev.ryanhcode.sable.companion.math.BoundingBox3d bounds) {
                  if (isActive()) updatePose();
                  super.getBoundingBox(bounds);
                }
              };
          var physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level);
          physics.addObject(box);
          helper.runAfterDelay(
              70,
              () -> {
                box.updatePose();
                double y = box.getPose().position().y();
                physics.removeObject(box);
                System.out.println("NATIVE_SEGMENT_POST_Y=" + y);
                helper.assertTrue(
                    y > pos.getY() + 2.05 && y < pos.getY() + 2.4,
                    "The middle of the segment did not land on the post: " + y);
                helper.succeed();
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 20, batch = "compact_ik")
  public static void native_ik_reaches_distinct_foot_targets(GameTestHelper helper) {
    var solver = new org.Azkerash.create_bioships.compat.letitwalk.CrabLegKinematics(1);
    var root = Vec3.ZERO;
    var a = new Vec3(1.4, -1.4, .15);
    var b = new Vec3(1.1, -.8, -.2);
    var first = solver.solve(root, Direction.EAST, a).getLast();
    var second = solver.solve(root, Direction.EAST, b).getLast();
    helper.assertTrue(
        first.distanceTo(a) < .15 && second.distanceTo(b) < .15,
        "Native IK did not follow target: " + first + " / " + second);
    helper.assertTrue(first.distanceTo(second) > .3, "IK remained in its rest pose");
    helper.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 480, batch = "crab_activation")
  public static void uncommanded_legs_ragdoll_and_brain_placement_preserves_position(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var base = new BlockPos(2304, 80, 128);
    for (int x = 143; x <= 145; x++) for (int z = 7; z <= 9; z++) level.setChunkForced(x, z, true);
    for (int x = -12; x <= 12; x++)
      for (int z = -12; z <= 12; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        20,
        () -> {
          var cells = new ArrayList<>(LocomotionFixture.place(level, base));
          var brainCell = base.offset(0, 3, 0);
          cells.remove(brainCell);
          level.removeBlock(brainCell, false);
          var extra = base.offset(0, 2, 2);
          level.setBlock(
              extra,
              org.Azkerash.create_bioships
                  .components
                  .ComponentRegistry
                  .CHITIN_ARMOR
                  .get()
                  .defaultBlockState(),
              3);
          cells.add(extra);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  base.offset(0, 2, 0),
                  cells,
                  new BoundingBox3i(base.offset(-2, 2, -1), base.offset(2, 3, 2)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          SubLevelData.hungerPaused(body, true);
          SubLevelLegs.request(body);
          helper.runAfterDelay(
              80,
              () -> {
                var before = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
                helper.assertTrue(
                    before.y - .5 < base.getY() + .4,
                    "Uncommanded legs held the body above the ground");
                level.setBlock(
                    core.above(),
                    org.Azkerash.create_bioships
                        .components
                        .ComponentRegistry
                        .BRAIN
                        .get()
                        .defaultBlockState(),
                    3);
                helper.runAfterDelay(
                    80,
                    () -> {
                      var after = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
                      helper.assertTrue(
                          Double.isFinite(after.lengthSqr()) && after.distanceTo(before) < 3,
                          "Adding brain launched asymmetric creature: " + after.subtract(before));
                      BrainMotorControls.move(body, Vec3.ZERO, 0, 200);
                      for (var pose : SubLevelLegs.poses(body))
                        if (pose.cell().getZ() < core.getZ())
                          level.setBlock(
                              pose.cell(),
                              level
                                  .getBlockState(pose.cell())
                                  .setValue(
                                      org.Azkerash.create_bioships
                                          .components
                                          .logic
                                          .legs
                                          .CrabLegBlock
                                          .HEIGHT,
                                      3),
                              3);
                      helper.runAfterDelay(
                          130,
                          () -> {
                            var front =
                                body.logicalPose().transformPosition(Vec3.atCenterOf(core.north()));
                            var back =
                                body.logicalPose().transformPosition(Vec3.atCenterOf(core.south()));
                            System.out.println("MIXED_LEG_TILT=" + (front.y - back.y));
                            helper.assertTrue(
                                front.y - back.y > .2, "Long front legs failed to tilt the body");
                            helper.runAfterDelay(
                                70,
                                () -> {
                                  helper.assertTrue(
                                      !SubLevelLegs.collisionOverlaps(
                                          body,
                                          new net.minecraft.world.phys.AABB(
                                              Vec3.atLowerCornerOf(base.offset(-12, -1, -12)),
                                              Vec3.atLowerCornerOf(base.offset(13, 0, 13))),
                                          .08),
                                      "Leg segment penetrated the floor");
                                  for (var pose : SubLevelLegs.poses(body))
                                    level.removeBlock(pose.cell(), false);
                                  helper.runAfterDelay(
                                      10,
                                      () -> {
                                        helper.assertTrue(
                                            SubLevelLegs.collisionShapes(body) == 0,
                                            "Removed legs left native collision shapes behind");
                                        helper.succeed();
                                      });
                                });
                          });
                    });
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 260, batch = "crab_legs")
  public static void native_ik_ground_contact_brain_movement_and_stop(GameTestHelper helper) {
    var level = helper.getLevel();
    var base = new BlockPos(2048, 80, 128);
    for (int x = 126; x <= 130; x++) for (int z = 6; z <= 10; z++) level.setChunkForced(x, z, true);
    for (int x = -15; x <= 15; x++)
      for (int z = -15; z <= 20; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    helper.runAfterDelay(
        10,
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
          SubLevelData.hungerPaused(body, true);
          SubLevelLegs.request(body);
          helper.assertTrue(
              SubLevelArmor.status(body).maximum() == 220, "Armor/leg capacity incorrect");
          helper.assertTrue(
              BrainMotorControls.move(body, new Vec3(0, 0, .8), 0, 200),
              "Brain rejected valid request");
          var start = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
          helper.runAfterDelay(
              100,
              () -> {
                var end = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
                System.out.println(
                    "CRAB_PHYSICS contacts="
                        + SubLevelLegs.contacts(body)
                        + " delta="
                        + end.subtract(start)
                        + " poses="
                        + SubLevelLegs.poses(body).size());
                helper.assertTrue(SubLevelLegs.poses(body).size() == 6, "Missing native IK poses");
                helper.assertTrue(
                    SubLevelLegs.collisionShapes(body) == 18,
                    "Every leg segment needs a native collision shape");
                for (var pose : SubLevelLegs.poses(body))
                  for (var point : pose.joints())
                    helper.assertTrue(
                        Double.isFinite(point.x + point.y + point.z), "Non-finite IK solution");
                helper.assertTrue(SubLevelLegs.contacts(body) > 0, "No actual ground contacts");
                helper.assertTrue(
                    end.z - start.z > .15, "Brain movement did not move physical body");
                BrainMotorControls.stop(body);
                helper.assertTrue(
                    BrainMotorControls.current(body).velocity().equals(Vec3.ZERO),
                    "Stop retained movement");
                BrainMotorControls.move(body, new Vec3(0, 0, 1), 0, 1);
                helper.runAfterDelay(
                    3,
                    () -> {
                      helper.assertTrue(
                          BrainMotorControls.current(body).velocity().equals(Vec3.ZERO),
                          "Expired request persisted");
                      BrainMotorControls.move(body, Vec3.ZERO, 0, 200);
                      for (var pose : SubLevelLegs.poses(body)) {
                        var state = level.getBlockState(pose.cell());
                        level.setBlock(
                            pose.cell(),
                            state.setValue(
                                org.Azkerash.create_bioships
                                    .components
                                    .logic
                                    .legs
                                    .CrabLegBlock
                                    .HEIGHT,
                                3),
                            3);
                      }
                      helper.runAfterDelay(
                          120,
                          () -> {
                            double clearance =
                                body.logicalPose().transformPosition(Vec3.atCenterOf(core)).y
                                    - .5
                                    - base.getY();
                            System.out.println(
                                "CRAB_EXTENDED_CLEARANCE="
                                    + clearance
                                    + " contacts="
                                    + SubLevelLegs.contacts(body));
                            helper.assertTrue(
                                clearance > 2.5 && clearance < 3.5,
                                "Extended legs failed to reach three-block clearance: "
                                    + clearance);
                            helper.assertTrue(
                                !SubLevelLegs.collisionOverlaps(
                                    body,
                                    new net.minecraft.world.phys.AABB(
                                        base.getX() - 15,
                                        base.getY() - 1,
                                        base.getZ() - 15,
                                        base.getX() + 16,
                                        base.getY(),
                                        base.getZ() + 21),
                                    .08),
                                "Extended leg segment penetrated the floor");
                            SubLevelHealth.damage(body, 999, "test");
                            helper.assertTrue(
                                !BrainMotorControls.move(body, new Vec3(0, 0, 1), 0, 20),
                                "Dead brain accepted control");
                            helper.succeed();
                          });
                    });
              });
        });
  }
}
