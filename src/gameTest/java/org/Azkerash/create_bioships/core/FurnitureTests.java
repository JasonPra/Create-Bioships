package org.Azkerash.create_bioships.core;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.furniture.FurnitureRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class FurnitureTests {
  @GameTest(template = "test_empty", batch = "estate_furniture", timeoutTicks = 40)
  public static void joined_tables_update_both_sides_and_preserve_water(GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 2, 2));
    var table = FurnitureRegistry.OSSUARY_TABLE.get();
    l.setBlock(p, table.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 3);
    l.setBlock(p.east(), table.defaultBlockState().setValue(BlockStateProperties.WEST, true), 3);
    h.assertTrue(
        l.getBlockState(p).getValue(BlockStateProperties.EAST),
        "Existing table did not join its new neighbor");
    h.assertTrue(
        l.getBlockState(p.east()).getValue(BlockStateProperties.WEST),
        "New table did not join existing table");
    l.removeBlock(p.east(), false);
    h.assertTrue(
        !l.getBlockState(p).getValue(BlockStateProperties.EAST),
        "Removed neighbor left a stale join");
    h.assertTrue(
        l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER),
        "Updating table connection lost waterlogging");
    h.succeed();
  }
}
