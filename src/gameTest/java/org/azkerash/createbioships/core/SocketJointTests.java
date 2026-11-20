package org.azkerash.createbioships.core;

import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.compat.letitwalk.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.legs.ModularJointEntity;
import org.azkerash.createbioships.mixin.JointChainLimitsAccess;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class SocketJointTests {
  @GameTest(template = "test_empty", timeoutTicks = 30, batch = "socket_joint_solver")
  public static void native_two_axis_solver_limits_reload_and_cached_step_envelope(
      GameTestHelper h) {
    var level = h.getLevel();
    var pos = h.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(pos, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, List.of(pos), new BoundingBox3i(pos, pos));
    var socket = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, socket);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var points = new double[][] {{0, 0, 0}, {.7, .3, 0}, {1.4, -.5, 0}};
    var axes = new double[][] {{0, 1, 0}, {0, 0, 1}, {0, 0, 1}};
    var joints = new ArrayList<BuiltLeg.Joint>();
    for (int i = 0; i < 3; i++) {
      joints.add(
          new BuiltLeg.Joint(
              i == 0 ? body.getUniqueId() : ids.get(i - 1),
              ids.get(i),
              Vec3.ZERO,
              Vec3.ZERO,
              new Vec3(axes[i][0], axes[i][1], axes[i][2]),
              i == 0));
    }
    var built =
        new BuiltLeg(
            joints,
            ids,
            points,
            axes,
            new double[] {1.7, -1.7, 0},
            true,
            new Vec3(0, 0, 1),
            new double[3]);
    built.segmentOffsets = new Vec3[] {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
    var leg = SocketJointRig.compact(body, socket, new LegRuntime(built, 0, 0, 1, false));
    h.assertTrue(
        leg.built.chain.size() == 6 && new HashSet<>(leg.built.segments).size() == 3,
        "Socket axes duplicated physical limb bodies");
    h.assertTrue(
        SocketJointRig.points(leg.built, Vec3.ZERO).size() == 4,
        "Pose packet no longer has four physical joint points");
    var limits = (JointChainLimitsAccess) (Object) leg.built.chain;
    h.assertTrue(
        Math.abs(limits.bioships$maximum()[0] - Math.PI * 2) < 1e-9
            && Math.abs(limits.bioships$maximum()[1] - Math.PI / 3) < 1e-9,
        "Socket defaults are not full swivel and ±60 bend");
    double[] target = {1.6, -1.5, .45};
    double error = leg.built.chain.solveRobust(target, leg.built.angles, 24, .04);
    h.assertTrue(error < .12, "Native socket IK failed a three-dimensional foot target: " + error);
    double behind =
        leg.built.chain.solveRobust(new double[] {-1.7, -1.7, 0}, leg.built.angles, 32, .04);
    h.assertTrue(behind < .12, "Full-swivel socket could not reach behind its mount: " + behind);
    leg.built.angles[0] = Math.PI + .02;
    var beforeWrap = leg.built.chain.forward(leg.built.angles, null);
    SocketJointRig.apply(leg.built);
    var afterWrap = leg.built.chain.forward(leg.built.angles, null);
    h.assertTrue(
        Math.abs(leg.built.angles[0] - (-Math.PI + .02)) < 1e-8
            && dev.letitwalk.core.JointChain.dist(
                    beforeWrap[beforeWrap.length - 1], afterWrap[afterWrap.length - 1])
                < 1e-8,
        "Full swivel changed the physical pose while crossing the angular seam");
    new SocketJointSettings(30, 15).save(body, SocketJointSettings.compact(socket, 1));
    SocketJointRig.apply(leg.built);
    h.assertTrue(
        Math.abs(limits.bioships$maximum()[2] - Math.PI / 12) < 1e-9
            && Math.abs(limits.bioships$maximum()[3] - Math.PI / 12) < 1e-9,
        "Editing one joint did not reach native IK limits");
    h.assertTrue(
        Math.abs(limits.bioships$maximum()[0] - Math.PI * 2) < 1e-9,
        "Editing one joint changed another joint");
    var saved = SocketJointRig.save(leg);
    var loaded = SocketJointRig.loadCompact(body, socket, saved);
    h.assertTrue(
        loaded != null
            && loaded.built.segments.equals(leg.built.segments)
            && loaded.built.chain.size() == 6,
        "Socket save/reload lost axes or segment identities");
    SocketJointRig.stepHeight(List.of(loaded));
    long work = dev.letitwalk.core.JointChain.WORK.sum();
    SocketJointRig.stepHeight(List.of(loaded));
    h.assertTrue(
        dev.letitwalk.core.JointChain.WORK.sum() == work,
        "Pathfinding recomputed the unchanged IK envelope");
    for (int i = 0; i < 3; i++) {
      new SocketJointSettings(0, 0).save(body, SocketJointSettings.compact(socket, i));
    }
    h.assertTrue(
        SocketJointRig.stepHeight(List.of(loaded)) == 0,
        "Locked joints retained stair-climbing capability");
    h.succeed();
  }

  @GameTest(template = "test_empty", timeoutTicks = 30, batch = "socket_joint_settings")
  public static void individual_tool_validation_native_move_and_removal_cleanup(GameTestHelper h) {
    var level = h.getLevel();
    var pos = h.absolutePos(new BlockPos(1, 3, 1));
    level.setBlock(pos, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        pos.east(), ComponentRegistry.LIMB_SOCKET.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        pos.west(), ComponentRegistry.LIMB_SOCKET.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            pos,
            List.of(pos, pos.east(), pos.west()),
            new BoundingBox3i(pos.west(), pos.east()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    var cell = core.east();
    var other = core.west();
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SocketTool"));
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        CreateBioships.LIMB_POSITIONER.get().getDefaultInstance());
    var world = body.logicalPose().transformPosition(Vec3.atCenterOf(cell));
    player.setPos(world.x, world.y + 1, world.z - 2);
    h.assertTrue(
        SocketJointActions.configure(player, cell, 0, 1, -1),
        "Positioner rejected a nearby socket");
    var key = SocketJointSettings.modular(body, cell);
    h.assertTrue(
        SocketJointSettings.get(body, key).bend() == 55
            && SocketJointSettings.get(body, SocketJointSettings.modular(body, other)).bend() == 60,
        "Per-component limits leaked to another socket");
    h.assertTrue(
        !SocketJointActions.configure(player, cell, 3, 1, 1),
        "Modular component accepted an invented joint index");
    var identity = ((ModularJointEntity) level.getBlockEntity(cell)).componentIdentity();
    var snapshot = level.getBlockEntity(cell).saveWithFullMetadata(level.registryAccess());
    var moved = cell.above();
    SocketJointSettings.duringMove(
        () -> {
          level.removeBlock(cell, false);
          level.setBlock(
              moved, ComponentRegistry.LIMB_SOCKET.get().defaultBlockState(), Block.UPDATE_ALL);
          level.getBlockEntity(moved).loadWithComponents(snapshot, level.registryAccess());
          return true;
        });
    h.assertTrue(
        ((ModularJointEntity) level.getBlockEntity(moved)).componentIdentity().equals(identity)
            && SocketJointSettings.get(body, SocketJointSettings.modular(body, moved)).bend() == 55,
        "Native component relocation lost grid-owned joint limits");
    player.setPos(world.x + 100, world.y, world.z);
    h.assertTrue(
        !SocketJointActions.configure(player, moved, 0, 1, 1),
        "Distant packet changed joint limits");
    player.setPos(world.x, world.y + 1, world.z - 2);
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
    h.assertTrue(
        !SocketJointActions.configure(player, moved, 0, 1, 1),
        "Packet without the Positioner changed joint limits");
    level.removeBlock(moved, false);
    level.setBlock(
        moved, ComponentRegistry.LIMB_SOCKET.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        !((ModularJointEntity) level.getBlockEntity(moved)).componentIdentity().equals(identity)
            && SocketJointSettings.get(body, SocketJointSettings.modular(body, moved))
                .equals(SocketJointSettings.DEFAULT),
        "Replacement inherited a removed socket's settings");
    h.assertTrue(
        !body.getUserDataTag()
            .getCompound(SocketJointSettings.DATA)
            .getCompound("Values")
            .contains(key),
        "Removed socket retained its settings record");
    h.succeed();
  }

  @net.minecraft.gametest.framework.GameTest(
      template = "test_empty",
      timeoutTicks = 100,
      batch = "leg_exhibit_doors")
  public static void both_leg_exhibits_have_usable_protected_double_doors(
      net.minecraft.gametest.framework.GameTestHelper h) {
    var level = h.getLevel();
    org.azkerash.createbioships.development.testing.LocomotionFixture.reset(level);
    org.azkerash.createbioships.development.testing.ModularLimbFixture.reset(level);
    for (var base :
        java.util.List.of(
            org.azkerash.createbioships.development.testing.LocomotionFixture.base(level),
            org.azkerash.createbioships.development.testing.ModularLimbFixture.base(level))) {
      for (int x = 0; x <= 1; x++) {
        var pos = base.offset(x, 0, -8);
        var door =
            org.azkerash.createbioships.development.testing.DevelopmentTestBlocks.GLASS_DOOR.get();
        h.assertTrue(
            level.getBlockState(pos).is(door) && level.getBlockState(pos.above()).is(door),
            "Missing double door half");
        h.assertTrue(
            level.getBlockState(pos).getDestroySpeed(level, pos) < 0, "Exhibit door is breakable");
        door.setOpen(null, level, level.getBlockState(pos), pos, true);
        h.assertTrue(
            level.getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                && level
                    .getBlockState(pos.above())
                    .getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
            "Door did not open both halves");
        door.setOpen(null, level, level.getBlockState(pos), pos, false);
        h.assertTrue(
            !level.getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
            "Door did not close");
      }
      h.assertTrue(
          level
              .getBlockState(base.offset(0, -1, -8))
              .is(net.minecraft.world.level.block.Blocks.BEDROCK),
          "Door has no protected floor");
    }
    h.succeed();
  }
}
