package org.azkerash.createbioships.core;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.compat.letitwalk.CompactLegCollisions;
import org.azkerash.createbioships.components.ComponentRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class FootContactOrderTests {
  @GameTest(template = "test_empty", batch = "foot_contact_fallback", timeoutTicks = 60)
  public static void distal_middle_and_proximal_contact_still_count_as_grounded(GameTestHelper h) {
    var level = h.getLevel();
    var origin = new BlockPos(23200, 100, 128);
    level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var owner =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    var parts = new ArrayList<ServerSubLevel>();
    var floors = new ArrayList<BlockPos>();
    for (int i = 0; i < 3; i++) {
      var at = origin.east(4 + i * 3);
      level.setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
      parts.add(
          SubLevelAssemblyHelper.assembleBlocks(level, at, List.of(at), new BoundingBox3i(at, at)));
      floors.add(at.below());
      level.setBlock(at.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    var lengths = new double[] {1, 1, 1};
    TerrainProbe.withIgnored(
        body -> body == owner || parts.contains(body),
        () -> {
          h.assertTrue(
              !CompactLegCollisions.grounded(owner, parts, lengths),
              "Suspended segments reported terrain contact");
          for (int contact : new int[] {2, 1, 0}) {
            level.setBlock(floors.get(contact), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            h.assertTrue(
                CompactLegCollisions.grounded(owner, parts, lengths),
                "Contact was lost on segment " + contact);
            level.setBlock(floors.get(contact), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          }
          h.assertTrue(
              !CompactLegCollisions.grounded(owner, List.of(), new double[0]),
              "An empty limb reported terrain contact");
        });
    h.succeed();
  }
}
