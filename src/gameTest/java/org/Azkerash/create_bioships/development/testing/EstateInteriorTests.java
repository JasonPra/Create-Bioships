package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.furniture.FurnitureRegistry;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class EstateInteriorTests {
  @GameTest(template = "test_empty", batch = "estate_library", timeoutTicks = 60)
  public static void library_has_fifteen_valid_shelves_and_floor_standing_lectern(
      GameTestHelper h) {
    var l = h.getLevel();
    var base = new BlockPos(9500, 80, 256);
    for (int x = -13; x <= 13; x++)
      for (int z = -13; z <= 13; z++)
        l.setBlock(base.offset(x, 23, z), Blocks.STONE.defaultBlockState(), 2);
    var oldChest = base.offset(-8, 24, 9);
    l.setBlock(oldChest, Blocks.CHEST.defaultBlockState(), 3);
    ((net.minecraft.world.Container) l.getBlockEntity(oldChest))
        .setItem(
            0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
    EstateInteriors.library(l, base);
    var p = EstateInteriors.enchantingTable(base);
    long sources =
        EnchantingTableBlock.BOOKSHELF_OFFSETS.stream()
            .filter(o -> EnchantingTableBlock.isValidBookShelf(l, p, o))
            .count();
    h.assertTrue(sources >= 15, "Insufficient real enchanting sources: " + sources);
    h.assertTrue(
        l.getBlockState(base.offset(3, 24, 5)).is(FurnitureRegistry.OSSUARY_LECTERN.get())
            && l.getBlockState(base.offset(3, 23, 5))
                .isFaceSturdy(l, base.offset(3, 23, 5), Direction.UP),
        "Reading stand is floating");
    var cabinet = (net.minecraft.world.Container) l.getBlockEntity(base.offset(2, 24, 10));
    h.assertTrue(
        cabinet.getItem(0).is(net.minecraft.world.item.Items.DIAMOND)
            && cabinet.getItem(0).getCount() == 3,
        "Library migration lost stored items");
    h.succeed();
  }
}
