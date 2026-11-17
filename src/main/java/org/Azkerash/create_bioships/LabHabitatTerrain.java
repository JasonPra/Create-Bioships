package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Deterministic terrain slices inside suspended developer enclosures. */
final class LabHabitatTerrain {
  static void land(ServerLevel level, BlockPos center) {
    int radius = EcosystemLab.RADIUS;
    for (int x = -radius; x <= radius; x++)
      for (int z = -radius; z <= radius; z++) {
        int height = (int) Math.round(3 * Math.sin(x * .11) + 2 * Math.cos(z * .14));
        boolean edge = Math.abs(x) == radius || Math.abs(z) == radius;
        for (int y = -12; y <= 20; y++) {
          var block =
              y == -12
                  ? Blocks.BEDROCK
                  : edge
                      ? Blocks.GLASS
                      : y < height - 3
                          ? Blocks.STONE
                          : y < height
                              ? Blocks.DIRT
                              : y == height ? Blocks.GRASS_BLOCK : Blocks.AIR;
          // Open rock alcoves supply shaded, navigable cache locations.
          if (!edge && x > 20 && x < 37 && Math.abs(z) < 5 && y > height - 5 && y < height - 1)
            block = Blocks.AIR;
          level.setBlock(center.offset(x, y, z), block.defaultBlockState(), 2);
        }
        if (!edge && Math.floorMod(x * 73 + z * 31, 29) == 0)
          level.setBlock(
              center.offset(x, height + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        if (Math.abs(x) < radius - 5
            && Math.abs(z) < radius - 5
            && Math.floorMod(x * 157 + z * 59, 431) == 0) {
          for (int y = 1; y <= 5; y++)
            level.setBlock(center.offset(x, height + y, z), Blocks.OAK_LOG.defaultBlockState(), 2);
          for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
              level.setBlock(
                  center.offset(x + dx, height + 5, z + dz),
                  Blocks.OAK_LEAVES.defaultBlockState(),
                  2);
        }
      }
    var source =
        level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
    for (int x = -radius; x < radius; x += 16)
      for (int z = -radius; z < radius; z += 16)
        level
            .getServer()
            .getCommands()
            .performPrefixedCommand(
                source,
                "fillbiome "
                    + (center.getX() + x)
                    + " "
                    + (center.getY() - 12)
                    + " "
                    + (center.getZ() + z)
                    + " "
                    + (center.getX() + x + 15)
                    + " "
                    + (center.getY() + 20)
                    + " "
                    + (center.getZ() + z + 15)
                    + " minecraft:forest");
  }
}
