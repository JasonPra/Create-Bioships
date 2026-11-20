package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.compat.letitwalk.*;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;
import org.Azkerash.create_bioships.development.testing.ModularLimbFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ModularWalkingTests {
  @GameTest(template = "test_empty", timeoutTicks = 1100, batch = "modular_continuous_walk")
  public static void repeated_flat_ground_steps_keep_limb_support(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(5120, 80, 128);
    for (int cx = 317; cx <= 323; cx++) {
      for (int cz = 6; cz <= 10; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -32; x <= 32; x++) {
      for (int z = -16; z <= 16; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    final dev.ryanhcode.sable.sublevel.ServerSubLevel[] subject = {null};
    final int[] blocked = {0};
    final double[] start = {0};
    h.onEachTick(
        () -> {
          long ticks = h.getTick();
          if (ticks == 20) {
            var cells = ModularLimbFixture.place(level, base);
            var body =
                SubLevelAssemblyHelper.assembleBlocks(
                    level,
                    base.above(4),
                    cells,
                    new BoundingBox3i(base.offset(-4, 1, -2), base.offset(4, 5, 2)));
            CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
            DevelopmentLifeSupport.pause(body);
            h.assertTrue(ModularLimbController.assemble(body), "Assembly failed");
            subject[0] = body;
          }
          if (ticks < 100) {
            return;
          }
          var body = subject[0];
          BrainMotorControls.move(body, new Vec3(ticks < 500 ? .6 : -.6, 0, 0), 0, 20);
          if (ModularLimbCollisions.obstructed(body)) {
            blocked[0]++;
          }
          var up = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0));
          h.assertTrue(up.y > .8, "Flat walking lost support: " + up.y);
          h.assertTrue(
              ModularLimbController.maximumJointSeparation(body) < .15,
              "Walking separated sockets");
          var rootCenter =
              body.logicalPose()
                  .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
          for (var part : ModularLimbController.segments(body)) {
            for (var cell : SubLevelHealth.blocks(part)) {
              if (level
                  .getBlockState(cell)
                  .is(
                      org.Azkerash.create_bioships.components.ComponentRegistry.LIMB_SOCKET
                          .get())) {
                h.assertTrue(
                    part.logicalPose().transformPosition(Vec3.atCenterOf(cell)).y
                        > rootCenter.y - 2,
                    "A knee collapsed to the floor during ordinary steps");
              }
            }
          }
          if (ticks == 100) {
            start[0] = body.logicalPose().position().x();
          }
          if (ticks % 100 == 0) {
            System.out.println(
                "MODULAR_WALK_TRACE t="
                    + ticks
                    + " x="
                    + body.logicalPose().position().x()
                    + " blocked="
                    + blocked[0]);
          }
          if (ticks == 500) {
            h.assertTrue(
                body.logicalPose().position().x() > start[0] + 2, "Continuous walking stalled");
          }
          if (ticks >= 900) {
            BrainMotorControls.stop(body);
            h.succeed();
          }
        });
  }
}
