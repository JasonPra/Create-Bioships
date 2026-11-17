package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CoreBlinkTests {
  @GameTest(template = "test_empty", timeoutTicks = 220)
  public static void core_only_blinks_after_assembly_and_closes_after_disassembly(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = new BlockPos(32, 80, 32);
    var idle = pos.east(8);
    level.getChunk(2, 2);
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= 3; z++)
        level.setBlock(pos.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
    level.setBlock(idle, Create_bioships.CORE.get().defaultBlockState(), 3);
    level.setBlock(pos, Create_bioships.CORE.get().defaultBlockState(), 3);
    helper.assertTrue(
        level.getBlockState(pos).getValue(CoreBlock.BLINK), "Unassembled core is not closed-eyed");
    helper.assertTrue(level.getBlockEntity(pos) == null, "Core acquired an unwanted block entity");
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, pos, java.util.List.of(pos), new BoundingBox3i(pos, pos));
    helper.assertTrue(body != null, "Sable could not assemble the core");
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    var logical = body.getPlot().getCenterBlock();
    sample(helper, body, logical, idle, new int[2], 0);
  }

  private static void sample(
      GameTestHelper helper,
      ServerSubLevel body,
      BlockPos pos,
      BlockPos idle,
      int[] counts,
      int tick) {
    helper.runAfterDelay(
        1,
        () -> {
          var level = helper.getLevel();
          var state = level.getBlockState(pos);
          helper.assertTrue(
              level.getBlockState(idle).getValue(CoreBlock.BLINK),
              "Unassembled core blinked or opened");
          if (tick >= 3 && tick <= 80)
            helper.assertTrue(
                !state.getValue(CoreBlock.BLINK), "Assembled core did not default open-eyed");
          counts[state.getValue(CoreBlock.BLINK) ? 1 : 0]++;
          if (tick < 139) {
            sample(helper, body, pos, idle, counts, tick + 1);
            return;
          }
          helper.assertTrue(
              counts[0] > 100 && counts[1] >= 7, "The original double-blink cycle did not run");
          helper.assertTrue(
              level.getBlockEntity(pos) == null, "Assembly added an unwanted block entity");
          var target = idle.east(2);
          dev.simulated_team.simulated.util.SimAssemblyHelper.disassembleSubLevel(
              level, body, pos, target, net.minecraft.world.level.block.Rotation.NONE, false);
          helper.assertTrue(
              level.getBlockState(target).getValue(CoreBlock.BLINK),
              "Disassembled core remained open-eyed");
          if (!body.isRemoved())
            SubLevelContainer.getContainer(level)
                .removeSubLevel(body, SubLevelRemovalReason.REMOVED);
          level.removeBlock(target, false);
          level.removeBlock(idle, false);
          helper.succeed();
        });
  }
}
