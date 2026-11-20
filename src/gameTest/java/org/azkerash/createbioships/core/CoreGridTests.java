package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.components.ComponentRegistry;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreGridTests {
  @GameTest(template = "test_empty", timeoutTicks = 180, batch = "multi_core_initial")
  public static void assembling_three_cores_separates_extras_without_moving_other_blocks(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(160, 80, 32);
    level.getChunk(10, 2);
    for (int x = -4; x <= 12; x++) {
      for (int z = -4; z <= 6; z++) {
        level.setBlock(origin.offset(x, -2, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    var blocks = new ArrayList<BlockPos>();
    for (int x = 0; x < 9; x++) {
      for (int z = 0; z < 3; z++) {
        var pos = origin.offset(x, 0, z);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        blocks.add(pos);
      }
    }
    for (int x : new int[] {0, 4, 8}) {
      var pos = origin.offset(x, 1, 1);
      level.setBlock(pos, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
      blocks.add(pos);
    }
    var parent =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin.above().south(),
            blocks,
            new BoundingBox3i(origin, origin.offset(8, 1, 2)));
    hold(level, parent);
    helper.runAfterDelay(3, () -> nearby(level, origin).forEach(body -> hold(level, body)));
    helper.runAfterDelay(
        40,
        () -> {
          verify(helper, origin, 3);
          int stones = 0;
          for (var holder : parent.getPlot().getLoadedChunks()) {
            for (var section : holder.getChunk().getSections()) {
              for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                    if (section.getBlockState(x, y, z).is(Blocks.STONE)) {
                      stones++;
                    }
                  }
                }
              }
            }
          }
          helper.assertTrue(
              stones == 27, "Detaching cores changed the original grid's other blocks");
          helper.runAfterDelay(
              40,
              () -> {
                verify(helper, origin, 3);
                cleanup(level, origin);
                helper.succeed();
              });
        });
  }

  @GameTest(template = "test_empty", timeoutTicks = 140, batch = "multi_core_added")
  public static void cores_added_to_existing_creature_detach_without_recreating_themselves(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(224, 80, 32);
    level.getChunk(14, 2);
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        level.setBlock(origin.offset(x, -2, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
      }
    }
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var parent =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    hold(level, parent);
    var original = parent.getPlot().getCenterBlock();
    helper.runAfterDelay(
        8,
        () -> {
          level.setBlock(
              original.east(), ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(
              original.west(), ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        });
    helper.runAfterDelay(12, () -> nearby(level, origin).forEach(body -> hold(level, body)));
    helper.runAfterDelay(
        45,
        () -> {
          verify(helper, origin, 3);
          helper.assertTrue(
              level.getBlockState(original).is(ComponentRegistry.CORE.get())
                  && CoreAssemblyRules.cores(parent).equals(List.of(original)),
              "Original creature did not retain its original core");
          cleanup(level, origin);
          helper.succeed();
        });
  }

  private static void hold(ServerLevel level, ServerSubLevel body) {
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
  }

  private static List<ServerSubLevel> nearby(ServerLevel level, BlockPos origin) {
    var result = new ArrayList<ServerSubLevel>();
    for (var entry : SubLevelContainer.getContainer(level).getAllSubLevels()) {
      if (entry instanceof ServerSubLevel body
          && !body.isRemoved()
          && CoreAssemblyRules.isCreature(body)
          && body.logicalPose()
                  .transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()))
                  .distanceToSqr(Vec3.atCenterOf(origin))
              < 1600) {
        result.add(body);
      }
    }
    return result;
  }

  private static void verify(GameTestHelper helper, BlockPos origin, int expected) {
    var bodies = nearby(helper.getLevel(), origin);
    helper.assertTrue(
        bodies.size() == expected, "Wrong number of separate creature grids: " + bodies.size());
    var identities = new HashSet<UUID>();
    for (var body : bodies) {
      helper.assertTrue(
          CoreAssemblyRules.cores(body).size() == 1, "Creature grid did not have exactly one core");
      identities.add(body.getUserDataTag().getUUID(CoreAssemblyRules.ID));
    }
    helper.assertTrue(identities.size() == expected, "Detached cores shared a creature identity");
  }

  private static void cleanup(ServerLevel level, BlockPos origin) {
    for (var body : nearby(level, origin)) {
      SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
  }
}
