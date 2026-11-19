package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.compat.letitwalk.*;
import org.Azkerash.create_bioships.core.brain.*;
import org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder;
import org.Azkerash.create_bioships.development.testing.ModularLimbFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class LocomotionWallTests {
  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "modular_wall_clearance")
  public static void live_wall_cancels_drive_and_native_sweep_cannot_tunnel(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(4096, 80, 128);
    for (int cx = 254; cx <= 258; cx++)
      for (int cz = 6; cz <= 10; cz++) level.setChunkForced(cx, cz, true);
    for (int x = -16; x <= 16; x++)
      for (int z = -16; z <= 16; z++)
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 3);
    final dev.ryanhcode.sable.sublevel.ServerSubLevel[] subject = {null};
    final double[] initial = {0};
    h.onEachTick(
        () -> {
          if (h.getTick() == 20) {
            var cells = ModularLimbFixture.place(level, base);
            var body =
                SubLevelAssemblyHelper.assembleBlocks(
                    level,
                    base.above(4),
                    cells,
                    new BoundingBox3i(base.offset(-4, 1, -2), base.offset(4, 5, 2)));
            CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
            SubLevelData.hungerPaused(body, true);
            h.assertTrue(ModularLimbController.assemble(body), "Assembly failed");
            subject[0] = body;
          }
          if (h.getTick() == 120) {
            var body = subject[0];
            var parts = ModularLimbController.segments(body);
            var part = parts.getFirst();
            var pose = part.logicalPose();
            var from = new Vec3(pose.position().x(), pose.position().y(), pose.position().z());
            h.assertTrue(
                ModularLimbCollisions.clearSweep(part, from, pose.orientation()),
                "Clear standing pose was blocked");
            // Wall is one block thick and the destination lies beyond it: endpoint-only tests
            // miss it.
            int wallX =
                (int)
                        Math.ceil(
                            org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.bounds(
                                    body)
                                .maxX)
                    + 1;
            for (int z = -16; z <= 16; z++)
              for (int y = 0; y <= 12; y++)
                level.setBlock(
                    new BlockPos(wallX, base.getY() + y, base.getZ() + z),
                    Blocks.GLASS.defaultBlockState(),
                    3);
            double across = wallX - from.x + 3;
            h.assertTrue(
                !ModularLimbCollisions.clearSweep(part, from.add(across, 0, 0), pose.orientation()),
                "Native pose sweep crossed a one-block glass wall");
            h.assertTrue(
                !SubLevelPathfinder.find(
                        body,
                        new Vec3(wallX + 3, base.getY() + 3, base.getZ()),
                        SubLevelPathfinder.Mode.GROUND)
                    .found(),
                "Planner routed the limb envelope through glass");
            // Move the wall close enough to invalidate an already-issued request.
            int closeX =
                (int)
                    Math.ceil(
                        org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.bounds(body)
                            .maxX);
            for (int z = -16; z <= 16; z++)
              for (int y = 0; y <= 12; y++)
                level.setBlock(
                    new BlockPos(closeX, base.getY() + y, base.getZ() + z),
                    Blocks.GLASS.defaultBlockState(),
                    3);
            h.assertTrue(
                !BrainMotorControls.move(body, new Vec3(2, 0, 0), 0, 100),
                "Live clearance accepted a drive into the wall");
            h.assertTrue(
                BrainMotorControls.current(body).velocity().lengthSqr() == 0,
                "Rejected drive retained its old velocity");
            h.assertTrue(
                BrainMotorControls.groundPostureEnabled(body),
                "Blocked movement disabled standing");
            // Repeated AI/manual pressure must remain bounded instead of accumulating into the
            // wall.
            initial[0] = body.logicalPose().position().x();
          }
          if (h.getTick() > 120 && h.getTick() <= 220)
            BrainMotorControls.move(subject[0], new Vec3(2, 0, 0), 0, 20);
          if (h.getTick() == 230) {
            var body = subject[0];
            h.assertTrue(
                body.logicalPose().position().x() < initial[0] + .5,
                "Repeated blocked drive forced the hull into the wall");
            h.assertTrue(
                ModularLimbController.maximumJointSeparation(body) < .1,
                "Blocked movement separated native joints");
            BrainMotorControls.obstructed(body);
            initial[0] = body.logicalPose().position().x();
            h.assertTrue(
                BrainMotorControls.move(body, new Vec3(-.6, 0, 0), 0, 100),
                "A blocked step locked out a collision-clear escape request");
          }
          if (h.getTick() == 320) {
            var body = subject[0];
            h.assertTrue(
                body.logicalPose().position().x() < initial[0] - .1,
                "Creature could not walk away after wall pressure stopped");
            h.assertTrue(
                ModularLimbController.maximumJointSeparation(body) < .15,
                "Escape separated the native joints");
            System.out.println(
                "MODULAR_WALL_PASS swept_glass=true live_drive_rejected=true standing=true"
                    + " escape=true");
            h.succeed();
          }
        });
  }
}
