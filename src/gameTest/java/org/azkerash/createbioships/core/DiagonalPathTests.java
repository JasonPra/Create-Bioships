package org.azkerash.createbioships.core;

import static org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder.Mode.*;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class DiagonalPathTests {
  @GameTest(template = "test_empty", batch = "diagonal_paths", timeoutTicks = 30)
  public static void diagonals_use_short_routes_without_crossing_ground_or_water_corners(
      GameTestHelper h) {
    SubLevelPathfinder.Space flat =
        (p, m) -> p.getY() == 0 && p.getX() >= 0 && p.getX() <= 8 && p.getZ() >= 0 && p.getZ() <= 8;
    var route = SubLevelPathfinder.search(flat, BlockPos.ZERO, new BlockPos(6, 0, 6), GROUND, 128);
    h.assertTrue(
        route.size() == 6 && route.getFirst().equals(new BlockPos(1, 0, 1)),
        "Open ground did not take six diagonal steps");
    SubLevelPathfinder.Space blocked =
        (p, m) -> flat.passable(p, m) && !p.equals(new BlockPos(1, 0, 0));
    var detour =
        SubLevelPathfinder.search(blocked, BlockPos.ZERO, new BlockPos(2, 0, 2), GROUND, 128);
    h.assertTrue(
        !detour.isEmpty() && !detour.getFirst().equals(new BlockPos(1, 0, 1)),
        "Ground path cut a blocked corner");
    SubLevelPathfinder.Space sealed =
        (p, m) -> p.equals(BlockPos.ZERO) || p.equals(new BlockPos(1, 0, 1));
    h.assertTrue(
        SubLevelPathfinder.search(sealed, BlockPos.ZERO, new BlockPos(1, 0, 1), GROUND, 32)
            .isEmpty(),
        "Diagonal crossed a closed corner");
    SubLevelPathfinder.Space water =
        (p, m) ->
            p.getX() >= 0
                && p.getX() <= 4
                && p.getY() >= 0
                && p.getY() <= 4
                && p.getZ() >= 0
                && p.getZ() <= 4;
    var swim = SubLevelPathfinder.search(water, BlockPos.ZERO, new BlockPos(3, 3, 3), WATER, 128);
    h.assertTrue(
        swim.size() == 3 && swim.getFirst().equals(new BlockPos(1, 1, 1)),
        "Water path missed 3D diagonals");
    SubLevelPathfinder.Space waterCorner =
        (p, m) -> water.passable(p, m) && !p.equals(new BlockPos(1, 1, 0));
    var swimDetour =
        SubLevelPathfinder.search(waterCorner, BlockPos.ZERO, new BlockPos(2, 2, 2), WATER, 128);
    h.assertTrue(
        !swimDetour.isEmpty() && !swimDetour.getFirst().equals(new BlockPos(1, 1, 1)),
        "Water path crossed an obstructed 3D corner");
    SubLevelPathfinder.Space edgeBlocked =
        new SubLevelPathfinder.Space() {
          public boolean passable(BlockPos p, SubLevelPathfinder.Mode m) {
            return flat.passable(p, m);
          }

          public boolean edgeClear(BlockPos a, BlockPos b, SubLevelPathfinder.Mode m) {
            return a.getX() == b.getX() || a.getZ() == b.getZ();
          }
        };
    var swept =
        SubLevelPathfinder.search(edgeBlocked, BlockPos.ZERO, new BlockPos(2, 0, 2), GROUND, 128);
    h.assertTrue(swept.size() == 4, "Diagonal planner ignored swept-edge collision checks");
    h.assertTrue(
        SubLevelPathfinder.search(flat, BlockPos.ZERO, new BlockPos(6, 0, 6), GROUND, 1).isEmpty(),
        "Diagonal search exceeded expansion budget");
    h.succeed();
  }
}
