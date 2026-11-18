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
                          80,
                          () -> {
                            double clearance =
                                body.logicalPose().transformPosition(Vec3.atCenterOf(core)).y
                                    - .5
                                    - base.getY();
                            System.out.println("CRAB_EXTENDED_CLEARANCE=" + clearance);
                            helper.assertTrue(
                                clearance > 2.5 && clearance < 3.5,
                                "Extended legs failed to reach three-block clearance: "
                                    + clearance);
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
