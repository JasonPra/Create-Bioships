package org.azkerash.createbioships.core;

import dev.letitwalk.ModContent;
import dev.letitwalk.content.*;
import dev.letitwalk.walker.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.compat.letitwalk.NativeLegPoseBatch;
import org.joml.Vector3d;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class NativeLegPoseBatchTests {
  private static final double EPSILON = 1e-7;
  private static final double PHYSICS_DT = .025;

  @GameTest(template = "test_empty", batch = "native_pose_batch", timeoutTicks = 100)
  public static void deferred_targets_match_native_with_shared_physical_segments(GameTestHelper h) {
    var level = h.getLevel();
    var origin = new BlockPos(23400, 100, 128);
    level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
    var root = stone(level, origin);
    var part = stone(level, origin.east(4));
    h.runAfterDelay(
        20,
        () -> {
          var handle = RigidBodyHandle.of(part);
          h.assertTrue(handle != null && handle.isValid(), "Missing native physics body");
          var velocity = new Vector3d(1, 2, 3);
          var spin = new Vector3d(.1, .2, .3);
          handle.addLinearAndAngularVelocity(
              velocity.sub(handle.getLinearVelocity(new Vector3d()), new Vector3d()),
              spin.sub(handle.getAngularVelocity(new Vector3d()), new Vector3d()));
          var deferred = context(level, root, part);
          var legacy = context(level, root, part);
          var expectedVelocity = handle.getLinearVelocity(new Vector3d());
          var expectedSpin = handle.getAngularVelocity(new Vector3d());
          var result = NativeLegPoseBatch.calculate(deferred, root, PHYSICS_DT);
          h.assertTrue(
              !NativeLegPoseBatch.defersBodyWrites(), "Deferred scope leaked after calculation");
          h.assertTrue(
              handle.getLinearVelocity(new Vector3d()).distance(expectedVelocity) < EPSILON,
              "Target calculation changed physical velocity");
          h.assertTrue(
              handle.getAngularVelocity(new Vector3d()).distance(expectedSpin) < EPSILON,
              "Target calculation changed physical spin");
          var nativeResult = BuiltLegDriver.substep(legacy, root, PHYSICS_DT);
          h.assertTrue(
              result.size() == 1 && nativeResult.size() == 1,
              "Repeated joint axes should resolve to one physical target");
          var actual = result.get(part.getUniqueId());
          var expected = nativeResult.get(part.getUniqueId());
          h.assertTrue(
              actual.position().distance(expected.position()) < EPSILON
                  && actual.rotationPoint().distance(expected.rotationPoint()) < EPSILON
                  && actual.velocity().distance(expected.velocity()) < EPSILON
                  && actual.spin().distance(expected.spin()) < EPSILON
                  && Math.abs(actual.turn().dot(expected.turn())) > 1 - EPSILON,
              "Deferred targets differ from the unmodified native driver");
          h.assertTrue(
              handle.getLinearVelocity(new Vector3d()).distance(expectedVelocity) > .1,
              "Native callers outside the deferred scope no longer apply velocity");
          for (int i = 0; i < 2; i++) {
            h.assertTrue(
                deferred
                        .legs
                        .getFirst()
                        .built
                        .prevDesired[i]
                        .distanceTo(legacy.legs.getFirst().built.prevDesired[i])
                    < EPSILON,
                "Native joint target history changed");
          }
          h.succeed();
        });
  }

  private static ServerSubLevel stone(ServerLevel level, BlockPos at) {
    level.setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    return SubLevelAssemblyHelper.assembleBlocks(level, at, List.of(at), new BoundingBox3i(at, at));
  }

  private static WalkerCoreBlockEntity context(
      ServerLevel level, ServerSubLevel root, ServerSubLevel part) {
    var cell = root.getPlot().getCenterBlock();
    var walker = new WalkerCoreBlockEntity(cell, ModContent.WALKER_CORE.get().defaultBlockState());
    walker.setLevel(level);
    // A compact bone appears twice in the native chain: swivel and bend share its UUID.
    var pivot = Vec3.atLowerCornerOf(cell);
    var joints =
        List.of(
            new BuiltLeg.Joint(
                root.getUniqueId(), part.getUniqueId(), pivot, pivot, new Vec3(0, 1, 0), true),
            new BuiltLeg.Joint(
                part.getUniqueId(), part.getUniqueId(), pivot, pivot, new Vec3(1, 0, 0), false));
    var leg =
        new BuiltLeg(
            joints,
            List.of(part.getUniqueId(), part.getUniqueId()),
            new double[][] {{0, 0, 0}, {0, -1, 0}},
            new double[][] {{0, 1, 0}, {1, 0, 0}},
            new double[] {0, -2, 0},
            true,
            new Vec3(0, 0, 1),
            new double[] {0, 0});
    var anchor = Vec3.atLowerCornerOf(part.getPlot().getCenterBlock()).add(.5, 0, .5);
    leg.segmentOffsets =
        new Vec3[] {new Vec3(0, 0, 0).subtract(anchor), new Vec3(0, -1, 0).subtract(anchor)};
    leg.angles[0] = .2;
    leg.angles[1] = -.4;
    leg.footKind = LegFootBlock.Kind.NORMAL;
    leg.footKindKnown = true;
    walker.legs.add(new LegRuntime(leg, 0, 0, 1, false));
    return walker;
  }
}
