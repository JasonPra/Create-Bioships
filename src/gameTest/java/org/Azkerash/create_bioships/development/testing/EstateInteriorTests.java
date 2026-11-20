package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
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
    for (int x = -13; x <= 13; x++) {
      for (int z = -13; z <= 13; z++) {
        l.setBlock(base.offset(x, 23, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
    }
    var oldChest = base.offset(-8, 24, 9);
    l.setBlock(oldChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
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

  @GameTest(template = "test_empty", batch = "estate_rooms", timeoutTicks = 60)
  public static void rooms_have_supported_appliances_and_clear_stair_entry(GameTestHelper h) {
    var l = h.getLevel();
    var base = new BlockPos(9600, 80, 256);
    for (int floor : new int[] {0, 12, 36}) {
      for (int x = -12; x <= 12; x++) {
        for (int z = -12; z <= 12; z++) {
          l.setBlock(
              base.offset(x, floor - 1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
      }
    }
    // Model the real stair exit; furnishing must leave these two cells clear.
    EstateInteriors.rooms(l, base);
    var oven = base.offset(-10, 13, 2);
    h.assertTrue(
        l.getBlockState(oven).is(Blocks.FURNACE)
            && l.getBlockState(oven.below()).isFaceSturdy(l, oven.below(), Direction.UP),
        "Kitchen appliance floats");
    h.assertTrue(
        l.getBlockState(base.offset(-3, 12, 0)).isAir()
            && l.getBlockState(base.offset(-3, 13, 0)).isAir(),
        "Furniture blocks stair exit");
    var foot = base.offset(-1, 36, 7);
    h.assertTrue(
        l.getBlockState(foot).is(FurnitureRegistry.OSSUARY_BED.get())
            && l.getBlockState(foot.north()).is(FurnitureRegistry.OSSUARY_BED.get()),
        "Bedroom lacks complete native bed");
    h.assertTrue(
        l.getBlockState(base.offset(-1, 12, 5)).is(FurnitureRegistry.OSSUARY_CHAIR.get()),
        "Dining room lacks usable seating");
    h.succeed();
  }
}
