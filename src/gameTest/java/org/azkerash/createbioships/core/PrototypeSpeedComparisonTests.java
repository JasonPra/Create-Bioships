package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.CrabLegBlock;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.legs.SubLevelLegs;
import org.azkerash.createbioships.core.locomotion.prototype.PrototypeLocomotion;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

/** Opt-in, simulated-time comparison; this measures travel and stability, not client FPS. */
@GameTestHolder(CreateBioships.MODID)
public final class PrototypeSpeedComparisonTests {
  private static final int SETTLE_TICKS = 160;
  private static final int MOVE_TICKS = 200;
  private static final int SAMPLE_TICKS = 5;

  @GameTestGenerator
  public static List<TestFunction> comparisons() {
    if (!Boolean.getBoolean("core.prototypeSpeedQa")) {
      return List.of();
    }
    var tests = new ArrayList<TestFunction>();
    add(tests, PrototypeLocomotion.Mode.NATIVE, .65);
    add(tests, PrototypeLocomotion.Mode.NATIVE, 2);
    add(tests, PrototypeLocomotion.Mode.NATIVE_FAST, 2);
    add(tests, PrototypeLocomotion.Mode.NATIVE_FAST, 6);
    add(tests, PrototypeLocomotion.Mode.PROTOTYPE, 2);
    add(tests, PrototypeLocomotion.Mode.PROTOTYPE, 6);
    add(tests, PrototypeLocomotion.Mode.PROTOTYPE, 4);
    return tests;
  }

  private static void add(List<TestFunction> tests, PrototypeLocomotion.Mode mode, double speed) {
    int index = tests.size();
    String name = "prototype_speed_" + index;
    // Separate batches prevent another moving fixture from competing for simulation time.
    tests.add(
        new TestFunction(
            name,
            name,
            "create_bioships:test_empty",
            500,
            0,
            true,
            helper -> run(helper, index, mode, speed)));
  }

  private static void run(
      GameTestHelper h, int index, PrototypeLocomotion.Mode mode, double speed) {
    System.out.println("PROTOTYPE_FIXTURE begin=" + index);
    var level = h.getLevel();
    var base = new BlockPos(24000 + index * 64, 80, 128);
    for (int x = (base.getX() - 16) >> 4; x <= (base.getX() + 16) >> 4; x++) {
      for (int z = (base.getZ() - 20) >> 4; z <= (base.getZ() + 130) >> 4; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -14; x <= 14; x++) {
      for (int z = -18; z <= 128; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          System.out.println("PROTOTYPE_FIXTURE assembling=" + index);
          var cells = new ArrayList<BlockPos>();
          var core = base.above(2);
          for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
              var cell = core.offset(x, -1, z);
              level.setBlock(
                  cell, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
              cells.add(cell);
            }
          }
          level.setBlock(core, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          cells.add(core);
          level.setBlock(
              core.west(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
          cells.add(core.west());
          for (int x : new int[] {-1, 1}) {
            for (int z : new int[] {-1, 1}) {
              var cell = base.offset(x, 1, z);
              level.setBlock(
                  cell,
                  ComponentRegistry.CRAB_LEG
                      .get()
                      .defaultBlockState()
                      .setValue(CrabLegBlock.HEIGHT, 1)
                      .setValue(
                          BlockStateProperties.HORIZONTAL_FACING,
                          z < 0 ? Direction.NORTH : Direction.SOUTH),
                  Block.UPDATE_ALL);
            }
          }
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  core,
                  cells,
                  new BoundingBox3i(base.offset(-1, 1, -1), base.offset(1, 2, 1)));
          CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
          DevelopmentLifeSupport.pause(body);
          SubLevelLegs.request(body);
          PrototypeLocomotion.set(body, mode);
          class Sample implements Runnable {
            private int tick;
            private Vec3 start;
            private java.util.Set<java.util.UUID> originalParts;
            private boolean turned;
            private double minimumUp = 1;
            private double minimumY = Double.POSITIVE_INFINITY;
            private double maximumY = Double.NEGATIVE_INFINITY;

            @Override
            public void run() {
              tick += SAMPLE_TICKS;
              if (tick % 40 == 0) {
                System.out.println("PROTOTYPE_SAMPLE index=" + index + " tick=" + tick);
              }
              var point =
                  body.logicalPose()
                      .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
              h.assertTrue(Double.isFinite(point.lengthSqr()), "Non-finite body pose");
              if (tick == SETTLE_TICKS) {
                h.assertTrue(
                    SubLevelLegs.segments(body).size() == 12, "Missing physical leg segments");
                start = point;
                originalParts =
                    SubLevelLegs.segments(body).stream()
                        .map(dev.ryanhcode.sable.sublevel.ServerSubLevel::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet());
              }
              if (tick >= SETTLE_TICKS && tick < SETTLE_TICKS + MOVE_TICKS) {
                if (index == 6 && tick == 240) {
                  turned = Math.abs(body.logicalPose().orientation().y()) > .2;
                  PrototypeLocomotion.set(body, PrototypeLocomotion.Mode.NATIVE);
                }
                if (index == 6 && tick == 280) {
                  reloadController(body);
                  h.assertTrue(
                      PrototypeLocomotion.get(body) == PrototypeLocomotion.Mode.NATIVE,
                      "Controller mode did not survive reconstruction");
                  PrototypeLocomotion.set(body, mode);
                  h.assertTrue(
                      originalParts.equals(
                          SubLevelLegs.segments(body).stream()
                              .map(dev.ryanhcode.sable.sublevel.ServerSubLevel::getUniqueId)
                              .collect(java.util.stream.Collectors.toSet())),
                      "Controller reload replaced physical limbs");
                }
                if (index != 6 || tick < 240 || tick >= 280) {
                  BrainMotorControls.move(
                      body,
                      new Vec3(0, 0, speed),
                      index == 6 && tick < 240 ? .7 : 0,
                      SAMPLE_TICKS * 3);
                }
                minimumUp =
                    Math.min(
                        minimumUp,
                        body.logicalPose()
                            .orientation()
                            .transform(new org.joml.Vector3d(0, 1, 0))
                            .y);
                minimumY = Math.min(minimumY, point.y);
                maximumY = Math.max(maximumY, point.y);
              }
              if (tick >= SETTLE_TICKS + MOVE_TICKS) {
                BrainMotorControls.stop(body);
                System.out.println(
                    "PROTOTYPE_SPEED mode="
                        + mode
                        + " requested="
                        + speed
                        + " actual="
                        + (point.z - start.z) / (MOVE_TICKS / 20.0)
                        + " minUp="
                        + minimumUp
                        + " yRange="
                        + (maximumY - minimumY));
                if (mode == PrototypeLocomotion.Mode.PROTOTYPE) {
                  h.assertTrue(minimumUp > .85, "Prototype lost upright support");
                  h.assertTrue(maximumY - minimumY < 1, "Prototype collapsed or launched");
                  h.assertTrue(
                      (point.z - start.z) / (MOVE_TICKS / 20.0) > speed * .65,
                      "Prototype could not sustain requested travel");
                }
                if (index == 6) {
                  h.assertTrue(turned, "Prototype ignored turn request");
                }
                // Native comparison cases report tuning without asserting an improvement.
                var container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                for (var part : List.copyOf(SubLevelCreatureParts.members(body))) {
                  container.removeSubLevel(
                      part, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
                h.succeed();
              } else {
                h.runAfterDelay(SAMPLE_TICKS, () -> run());
              }
            }
          }
          h.runAfterDelay(SAMPLE_TICKS, new Sample());
        });
  }

  private static void reloadController(dev.ryanhcode.sable.sublevel.ServerSubLevel body) {
    try {
      var field = SubLevelLegs.class.getDeclaredField("CONTROLLERS");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      var controllers =
          (java.util.Map<
                  dev.ryanhcode.sable.sublevel.ServerSubLevel,
                  org.azkerash.createbioships.compat.letitwalk.CompactLimbController>)
              field.get(null);
      var old = controllers.remove(body);
      old.save();
      old.close();
      controllers.put(
          body, new org.azkerash.createbioships.compat.letitwalk.CompactLimbController(body));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
