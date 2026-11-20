package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.compat.letitwalk.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.*;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;
import org.azkerash.createbioships.development.testing.ModularLimbFixture;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class ModularLimbTests {
  @GameTest(template = "test_empty", timeoutTicks = 260, batch = "modular_native_limbs")
  public static void native_assembly_shared_brain_physical_joints_and_death(GameTestHelper h) {
    var level = h.getLevel();
    var base = new BlockPos(3456, 80, 128);
    for (int cx = 215; cx <= 217; cx++) {
      for (int cz = 7; cz <= 9; cz++) {
        level.setChunkForced(cx, cz, true);
      }
    }
    for (int x = -12; x <= 12; x++) {
      for (int z = -12; z <= 12; z++) {
        level.setBlock(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    h.runAfterDelay(
        20,
        () -> {
          var cells = ModularLimbFixture.place(level, base);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level,
                  base.above(4),
                  cells,
                  new BoundingBox3i(base.offset(-4, 1, -2), base.offset(4, 5, 2)));
          CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
          DevelopmentLifeSupport.pause(body);
          double capacity = SubLevelHealth.status(body).maximum();
          h.assertTrue(
              ModularLimbController.assemble(body),
              "Native assembler rejected our joint/segment/foot topology");
          h.assertTrue(ModularLimbController.limbCount(body) == 4, "Expected four modular limbs");
          h.assertTrue(
              ModularLimbController.axisCount(body) == 16,
              "Synovial sockets did not expose both native axes");
          h.assertTrue(
              ModularLimbController.segments(body).size() == 8,
              "Expected two linked sublevels per limb");
          h.assertTrue(
              SubLevelHealth.status(body).maximum() == capacity,
              "Assembly changed creature health capacity");
          h.runAfterDelay(
              100,
              () -> {
                h.assertTrue(
                    SubLevelBrains.get(body).inventory().isEmpty()
                        && BrainMotorControls.current(body).expires() == 0,
                    "Idle modular specimen unexpectedly has a card or movement request");
                h.assertTrue(
                    BrainMotorControls.groundPostureEnabled(body),
                    "Empty brain did not enable posture");
                var standing = body.logicalPose().position();
                var up = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 1, 0));
                System.out.println(
                    "MODULAR_IDLE_STAND y="
                        + standing.y()
                        + " up="
                        + up.y
                        + " jointGap="
                        + ModularLimbController.maximumJointSeparation(body));
                h.assertTrue(
                    ModularLimbController.maximumJointSeparation(body) < .08,
                    "Native modular pivots separated while idle");
                var foot = ComponentRegistry.LIMB_FOOT.get().defaultBlockState();
                h.assertTrue(
                    foot.getCollisionShape(level, body.getPlot().getCenterBlock())
                            .max(Direction.Axis.Y)
                        == 1,
                    "Foot collision geometry does not reach the adjoining limb segment");
                h.assertTrue(
                    standing.y() > base.getY() + 1.2 && standing.y() < base.getY() + 5 && up.y > .8,
                    "Modular legs did not stand without a behavior: " + standing);
                h.assertTrue(
                    ModularLimbController.jointCount(body) == 8,
                    "Native articulated limb chain was not created");
                for (var part : ModularLimbController.segments(body)) {
                  h.assertTrue(
                      SubLevelCreatureParts.root(part) == body,
                      "Native segment lost creature membership");
                  h.assertTrue(
                      SubLevelBrains.get(part) == SubLevelBrains.get(body),
                      "Segment brain was independent");
                }
                h.assertTrue(
                    BrainMotorControls.move(body, new Vec3(0, 0, .6), .25, 120),
                    "Brain rejected modular locomotion");
                var start = body.logicalPose().position().get(new org.joml.Vector3d());
                var facing =
                    body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 0, -1));
                h.runAfterDelay(
                    100,
                    () -> {
                      var end = body.logicalPose().position();
                      System.out.println(
                          "MODULAR_MOVEMENT start="
                              + start
                              + " end="
                              + end
                              + " joints="
                              + ModularLimbController.jointCount(body));
                      h.assertTrue(
                          Double.isFinite(end.lengthSquared()) && end.distance(start) < 20,
                          "Modular limbs launched or corrupted the body");
                      h.assertTrue(
                          Math.hypot(end.x() - start.x, end.z() - start.z) > .15,
                          "Native gait and suspension did not move the creature");
                      var turned =
                          body.logicalPose()
                              .orientation()
                              .transform(new org.joml.Vector3d(0, 0, -1));
                      double yawDelta =
                          Math.atan2(turned.x, turned.z) - Math.atan2(facing.x, facing.z);
                      yawDelta = Math.atan2(Math.sin(yawDelta), Math.cos(yawDelta));
                      System.out.println(
                          "MODULAR_YAW_DELTA="
                              + yawDelta
                              + " jointGap="
                              + ModularLimbController.maximumJointSeparation(body));
                      h.assertTrue(
                          ModularLimbController.maximumJointSeparation(body) < .08,
                          "Native modular pivots separated while walking");
                      h.assertTrue(
                          Math.abs(yawDelta) > .05, "Native yaw control did not turn the creature");
                      BrainMotorControls.stop(body);
                      SubLevelData.trust(body, 12.5);
                      ModularLimbController.stopping(
                          new net.neoforged.neoforge.event.server.ServerStoppingEvent(
                              level.getServer()));
                      h.runAfterDelay(
                          10,
                          () -> {
                            h.assertTrue(
                                ModularLimbController.limbCount(body) == 4
                                    && ModularLimbController.jointCount(body) == 8,
                                "Reloading saved native limbs lost their joints");
                            h.assertTrue(
                                SubLevelData.trust(ModularLimbController.segments(body).getFirst())
                                    == 12.5,
                                "Reloaded limb lost shared identity");
                            h.assertTrue(
                                BrainMotorControls.current(body).expires() == 0
                                    && BrainMotorControls.groundPostureEnabled(body),
                                "Stopping movement disabled modular standing");
                            h.assertTrue(
                                body.logicalPose().position().y() > base.getY() + 1.2,
                                "Idle modular creature collapsed after reload");
                            SubLevelHealth.damage(body, 10000, "test_death");
                            h.assertTrue(
                                !BrainMotorControls.groundPostureEnabled(body),
                                "Dead modular creature retained standing control");
                            h.assertTrue(
                                ModularLimbController.segments(body).stream()
                                    .allMatch(p -> SubLevelHealth.status(p).dead()),
                                "Linked limbs survived creature death");
                            h.runAfterDelay(
                                10,
                                () -> {
                                  h.assertTrue(
                                      ModularLimbController.passiveJointCount(body) == 8,
                                      "Death did not restore physical ragdoll joints");
                                  h.succeed();
                                });
                          });
                    });
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 40, batch = "modular_positioner")
  public static void native_component_types_and_restricted_positioner(GameTestHelper h) {
    var level = h.getLevel();
    var pos = h.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(pos, ComponentRegistry.LIMB_JOINT.get().defaultBlockState(), Block.UPDATE_ALL);
    var joint = (ModularJointEntity) level.getBlockEntity(pos);
    h.assertTrue(
        joint.getType() == ComponentRegistry.MODULAR_JOINT_ENTITY.get(),
        "Joint entity registered under native type");
    joint.setRestOffset(45);
    h.assertTrue(joint.restOffset() == 45, "Native rest-pose API did not retain angle");
    level.setBlock(
        pos.east(), ComponentRegistry.LIMB_FOOT.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        level.getBlockEntity(pos.east()).getType() == ComponentRegistry.MODULAR_FOOT_ENTITY.get(),
        "Foot entity type mismatch");
    var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
    player.setPos(Vec3.atCenterOf(pos));
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        org.azkerash.createbioships.CreateBioships.LIMB_POSITIONER.get().getDefaultInstance());
    h.assertTrue(
        LimbPositionerActions.adjust(player, pos, 5) && joint.restOffset() == 50,
        "Our Positioner did not use native pose adjustment");
    var other = pos.south();
    level.setBlock(
        other, dev.letitwalk.ModContent.LEG_HINGE.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        !LimbPositionerActions.adjust(player, other, 5),
        "Our Positioner modified another mod's limb");
    var core = pos.offset(4, 0, 0);
    var armor = core.north();
    var socket = core.north(2);
    level.setBlock(core, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        armor, ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(socket, ComponentRegistry.CRAB_LEG.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, core, List.of(core, armor, socket), new BoundingBox3i(socket, core));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(body);
    var logical = body.getPlot().getCenterBlock().north(2);
    player.setPos(body.logicalPose().transformPosition(Vec3.atCenterOf(logical)));
    h.assertTrue(
        LimbPositionerActions.configure(player, logical, 0, 1),
        "Positioner rejected compact height setting");
    LimbPositionerActions.configure(player, logical, 1, 5);
    LimbPositionerActions.configure(player, logical, 2, 1);
    LimbPositionerActions.configure(player, logical, 3, -1);
    var tuning = LimbTuning.get(level, logical);
    h.assertTrue(
        level.getBlockState(logical).getValue(CrabLegBlock.HEIGHT) == 2
            && tuning.restAngle() == 5
            && tuning.stiffness() == 1.25
            && tuning.damping() == .75,
        "Compact settings did not persist on the grid");
    var loose = pos.offset(10, 0, 0);
    level.setBlock(loose, ComponentRegistry.LIMB_JOINT.get().defaultBlockState(), Block.UPDATE_ALL);
    var unowned =
        SubLevelAssemblyHelper.assembleBlocks(
            level, loose, List.of(loose), new BoundingBox3i(loose, loose));
    var looseCell = unowned.getPlot().getCenterBlock();
    new LimbTuning(0, 1.5, 1.25).save(level, looseCell);
    h.assertTrue(
        LimbTuning.get(level, looseCell).stiffness() == 1.5 && !SubLevelData.isLiving(unowned),
        "Tuning a plain sublevel failed or marked it living");
    h.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "modular_exhibit")
  public static void visible_modular_exhibit_start_and_reset(GameTestHelper h) {
    var level = h.getLevel();
    ModularLimbFixture.reset(level);
    h.runAfterDelay(
        10,
        () -> {
          h.assertTrue(
              ModularLimbFixture.body(level) == null, "Reset exhibit was already assembled");
          h.assertTrue(ModularLimbFixture.start(level) == 1, "Modular exhibit START failed");
          var body = ModularLimbFixture.body(level);
          h.assertTrue(
              ModularLimbController.limbCount(body) == 4, "Exhibit did not build native limbs");
          h.assertTrue(
              SubLevelBrains.get(body)
                  .inventory()
                  .getItem(0)
                  .is(ComponentRegistry.WANDER_BEHAVIOR_CARD.get()),
              "Exhibit omitted Wander");
          h.runAfterDelay(
              20,
              () -> {
                var parts = SubLevelCreatureParts.members(body);
                ModularLimbFixture.reset(level);
                h.assertTrue(
                    ModularLimbFixture.body(level) == null
                        && parts.stream().allMatch(p -> p.isRemoved()),
                    "Reset left linked bodies behind");
                h.succeed();
              });
        });
  }
}
