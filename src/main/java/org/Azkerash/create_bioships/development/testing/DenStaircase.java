package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;

/** Two-wide, half-block rises around a solid core; each circuit climbs exactly one storey. */
final class DenStaircase {
  record Step(int x, int z, double height) {
    Vec3 feet(BlockPos origin) {
      return new Vec3(origin.getX() + x + .5, origin.getY() + height, origin.getZ() + z + .5);
    }
  }

  private DenStaircase() {}

  static List<Step> route(int height) {
    var ring = new ArrayList<BlockPos>();
    for (int x = 3; x >= -3; x--) ring.add(new BlockPos(x, 0, 3));
    for (int z = 2; z >= -3; z--) ring.add(new BlockPos(-3, 0, z));
    for (int x = -2; x <= 3; x++) ring.add(new BlockPos(x, 0, -3));
    for (int z = -2; z <= 2; z++) ring.add(new BlockPos(3, 0, z));
    var result = new ArrayList<Step>();
    for (int n = 0; n <= height * 2; n++) {
      var p = ring.get(n % ring.size());
      result.add(new Step(p.getX(), p.getZ(), n * .5));
    }
    return List.copyOf(result);
  }

  static void build(ServerLevel l, BlockPos origin, int height, boolean vault) {
    // Clear the whole well first. Never clear headroom after placing another tread.
    for (int x = -5; x <= 5; x++)
      for (int z = -5; z <= 5; z++)
        for (int y = -1; y <= height + 2; y++) {
          boolean wall = Math.abs(x) == 5 || Math.abs(z) == 5;
          boolean core = Math.abs(x) <= 2 && Math.abs(z) <= 2;
          l.setBlock(
              origin.offset(x, y, z),
              (wall || core || y == -1 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR)
                  .defaultBlockState(),
              2);
        }
    for (var step : route(height)) {
      int y = (int) Math.ceil(step.height()) - 1;
      var slab =
          Blocks.POLISHED_BLACKSTONE_BRICK_SLAB
              .defaultBlockState()
              .setValue(
                  BlockStateProperties.SLAB_TYPE,
                  step.height() % 1 == 0 ? SlabType.TOP : SlabType.BOTTOM);
      int[] xs =
          Math.abs(step.x()) == 3
              ? new int[] {step.x(), step.x() + Integer.signum(step.x())}
              : new int[] {step.x()};
      int[] zs =
          Math.abs(step.z()) == 3
              ? new int[] {step.z(), step.z() + Integer.signum(step.z())}
              : new int[] {step.z()};
      for (int x : xs) for (int z : zs) l.setBlock(origin.offset(x, y, z), slab, 2);
    }
    // Inset fungal light wells illuminate every flight without protruding into the walkway.
    for (int y = 2; y <= height + 1; y += 4)
      for (var face : Direction.Plane.HORIZONTAL) {
        var p = origin.offset(face.getStepX() * 2, y, face.getStepZ() * 2);
        l.setBlock(p, Blocks.SHROOMLIGHT.defaultBlockState(), 2);
      }
    if (vault) {
      open(l, origin, 0, 3, 4, 5);
      open(l, origin, height, 3, 4, 5);
      open(l, origin, height, 1, 2, -5);
      // The last partial circuit meets a level east-side landing, outside the solid core.
      for (int x = 3; x <= 4; x++) for (int z = -4; z <= 5; z++) floor(l, origin, x, height, z);
      for (int x = 1; x <= 4; x++) for (int z = -5; z <= -3; z++) floor(l, origin, x, height, z);
    } else {
      for (int y = 0; y <= height; y += 12) open(l, origin, y, 3, 4, 5);
    }
  }

  private static void floor(ServerLevel l, BlockPos b, int x, int y, int z) {
    l.setBlock(b.offset(x, y - 1, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
  }

  private static void open(ServerLevel l, BlockPos b, int y, int minX, int maxX, int z) {
    for (int x = minX; x <= maxX; x++) {
      floor(l, b, x, y, z);
      for (int head = 0; head < 4; head++)
        l.setBlock(b.offset(x, y + head, z), Blocks.AIR.defaultBlockState(), 2);
    }
  }
}
