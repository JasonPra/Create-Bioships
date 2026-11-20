package org.azkerash.createbioships.core;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.flippers.FlipperThrust;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class FlipperThrustTests {
  @GameTest(template = "test_empty")
  public static void turning_selects_one_flipper_per_side_and_reverses_the_pair(GameTestHelper h) {
    var fins = new ArrayList<FlipperThrust.Fin>();
    for (int x : new int[] {-2, 2}) {
      for (int z : new int[] {-1, 1}) {
        fins.add(new FlipperThrust.Fin(new BlockPos(x, 0, z), new Vec3(x, 0, z), 10));
      }
    }
    var forward = new Vec3(0, 0, 1);
    var right = FlipperThrust.allocate(fins, forward, 0, 12, 1);
    h.assertTrue(
        right.size() == 2
            && right.containsKey(new BlockPos(-2, 0, 1))
            && right.containsKey(new BlockPos(2, 0, -1)),
        "Wrong turning pair");
    double net = right.values().stream().mapToDouble(Double::doubleValue).sum();
    double torque =
        right.entrySet().stream()
            .mapToDouble(entry -> -entry.getKey().getX() * entry.getValue())
            .sum();
    h.assertTrue(
        Math.abs(net) < 1e-6 && Math.abs(torque - 12) < 1e-5, "Pair failed balanced yaw torque");
    var left = FlipperThrust.allocate(fins, forward, 0, -12, -1);
    h.assertTrue(
        left.containsKey(new BlockPos(-2, 0, -1)) && left.containsKey(new BlockPos(2, 0, 1)),
        "Opposite turn did not reverse diagonal pair");
    h.succeed();
  }

  @GameTest(template = "test_empty")
  public static void unequal_leverage_and_weak_flippers_get_different_bounded_efforts(
      GameTestHelper h) {
    var left = new BlockPos(-1, 0, 0);
    var right = new BlockPos(3, 0, 0);
    var forward = new Vec3(0, 0, 1);
    var fins =
        List.of(
            new FlipperThrust.Fin(left, new Vec3(-1, 0, 0), 10),
            new FlipperThrust.Fin(right, new Vec3(3, 0, 0), 10));
    var thrust = FlipperThrust.allocate(fins, forward, 8, 0, 0);
    h.assertTrue(
        Math.abs(thrust.get(left) - 6) < 1e-5 && Math.abs(thrust.get(right) - 2) < 1e-5,
        "Unequal leverage was not compensated");
    var weak = List.of(new FlipperThrust.Fin(left, new Vec3(-1, 0, 0), 2), fins.getLast());
    thrust = FlipperThrust.allocate(weak, forward, 8, 0, 0);
    h.assertTrue(
        Math.abs(thrust.get(left)) <= 2
            && Math.abs(thrust.get(left) - 3 * thrust.get(right)) < 1e-5,
        "Weak flipper caused unbounded thrust or a forced sideways turn");
    var centered =
        FlipperThrust.allocate(
            List.of(new FlipperThrust.Fin(BlockPos.ZERO, Vec3.ZERO, 10)), forward, 0, 100, 1);
    h.assertTrue(
        Math.abs(centered.getOrDefault(BlockPos.ZERO, 0d)) < 1e-6,
        "Zero-leverage flipper invented yaw torque");
    h.succeed();
  }
}
