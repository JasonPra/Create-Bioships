package org.Azkerash.create_bioships.core;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class EyeWaterTests {
  @GameTest(template = "test_empty", timeoutTicks = 220, batch = "unassembled_wet_eyes")
  public static void supported_unassembled_eyes_survive_flowing_water_but_still_need_support(
      GameTestHelper h) {
    var level = h.getLevel();
    var a = h.absolutePos(new BlockPos(1, 3, 1));
    var b = a.east(3);
    for (var p : java.util.List.of(a, b)) {
      level.setBlock(
          p.south(),
          com.github.elenterius.biomancy.init.ModBlocks.PACKED_FLESH.get().defaultBlockState(),
          3);
      level.setBlock(
          p,
          (p.equals(a) ? ComponentRegistry.LAND_EYE : ComponentRegistry.AQUATIC_EYE)
              .get()
              .defaultBlockState()
              .setValue(BlockStateProperties.FACING, Direction.NORTH),
          3);
      level.setBlock(p.above(), Blocks.WATER.defaultBlockState(), 3);
    }
    level.setBlock(b, Blocks.WATER.defaultBlockState(), 3);
    var context =
        new net.minecraft.world.item.context.BlockPlaceContext(
            level,
            null,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            ComponentRegistry.AQUATIC_EYE_ITEM.get().getDefaultInstance(),
            new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(b.south()),
                Direction.NORTH,
                b.south(),
                false));
    var placed = ComponentRegistry.AQUATIC_EYE.get().getStateForPlacement(context);
    h.assertTrue(
        placed != null && placed.getValue(BlockStateProperties.WATERLOGGED),
        "Underwater placement did not retain water");
    level.setBlock(b, placed, 3);
    h.runAfterDelay(
        120,
        () -> {
          for (var p : java.util.List.of(a, b))
            h.assertTrue(
                level.getBlockState(p).getBlock()
                        instanceof org.Azkerash.create_bioships.components.logic.eye.EyeBlock
                    && (!p.equals(b)
                        || level.getBlockState(p).getValue(BlockStateProperties.WATERLOGGED)),
                "Flow destroyed or failed to waterlog unassembled eye: "
                    + level.getBlockState(p)
                    + " support="
                    + level.getBlockState(p.south())
                    + " above="
                    + level.getBlockState(p.above()));
          level.removeBlock(a.south(), false);
          level.removeBlock(b.south(), false);
          h.runAfterDelay(
              5,
              () -> {
                for (var p : java.util.List.of(a, b))
                  h.assertTrue(
                      !(level.getBlockState(p).getBlock()
                          instanceof org.Azkerash.create_bioships.components.logic.eye.EyeBlock),
                      "Waterlogged eye survived without solid support");
                h.succeed();
              });
        });
  }
}
