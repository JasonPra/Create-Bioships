package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;
import org.azkerash.createbioships.development.testing.LocomotionFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class SocketTerrainTests {
  @GameTest(template = "test_empty", timeoutTicks = 1500, batch = "socket_terrain")
  public static void native_compact_legs_walk_onto_a_one_block_ledge(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(4608, 80, 128);
    for (int cx = 286; cx <= 290; cx++) {
      for (int cz = 6; cz <= 10; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -16; x <= 24; x++) {
      for (int z = -14; z <= 14; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        if (x >= 4) {
          level.setBlock(
              base.offset(x, 0, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        }
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var cells = LocomotionFixture.place(level, base);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  base.above(2),
                  cells,
                  new BoundingBox3i(base.offset(-2, 2, -1), base.offset(2, 3, 1)));
          var core = body.getPlot().getCenterBlock();
          CoreAssemblyRules.mark(level, core);
          DevelopmentLifeSupport.pause(body);
          SubLevelLegs.request(body);
          class Trial implements Runnable {
            int tick;

            public void run() {
              tick += 5;
              if (tick >= 100) {
                BrainMotorControls.move(body, new Vec3(.5, 0, 0), 0, 20);
              }
              var at = body.logicalPose().transformPosition(Vec3.atCenterOf(core));
              var up = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0));
              if (tick % 50 == 0) {
                System.out.println(
                    "NATIVE_STEP_TRIAL tick="
                        + tick
                        + " x="
                        + (at.x - base.getX())
                        + " y="
                        + (at.y - base.getY())
                        + " up="
                        + up.y);
              }
              if (tick >= 100) {
                h.assertTrue(
                    up.y > .8, "Native stepping lost upright support at " + tick + ": " + up.y);
                var obstacle =
                    new AABB(
                        base.getX() + 4,
                        base.getY(),
                        base.getZ() - 14,
                        base.getX() + 25,
                        base.getY() + 1,
                        base.getZ() + 15);
                if (SubLevelLegs.collisionOverlaps(body, obstacle, .08)) {
                  h.fail(
                      "Native stepping hit the ledge at "
                          + tick
                          + " core="
                          + at
                          + " "
                          + SubLevelLegs.collisionDetails(body, obstacle));
                }
              }
              if ((tick >= 600 && at.x > base.getX() + 9) || tick >= 1300) {
                BrainMotorControls.stop(body);
                h.assertTrue(at.x > base.getX() + 8, "Native legs did not cross the step: " + at);
                h.assertTrue(
                    at.y > base.getY() + 2.2 && at.y < base.getY() + 3.8,
                    "Creature did not stand on the raised floor: " + at);
                h.succeed();
                return;
              }
              h.runAfterDelay(5, () -> this.run());
            }
          }
          h.runAfterDelay(5, new Trial());
        });
  }
}
