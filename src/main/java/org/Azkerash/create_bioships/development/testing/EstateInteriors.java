package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import org.Azkerash.create_bioships.furniture.FurnitureRegistry;

/** Authored furniture layouts, independent of the vault structure and its living subjects. */
public final class EstateInteriors {
  private EstateInteriors() {}

  public static BlockPos enchantingTable(BlockPos surface) {
    return surface.offset(7, 24, 7);
  }

  private static void put(ServerLevel l, BlockPos p, Block b) {
    l.setBlock(p, b.defaultBlockState(), 3);
  }

  private static void face(ServerLevel l, BlockPos p, Block b, Direction d) {
    var state = b.defaultBlockState();
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
      state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    else if (state.hasProperty(BlockStateProperties.FACING))
      state = state.setValue(BlockStateProperties.FACING, d);
    l.setBlock(p, state, 3);
  }

  private static boolean stairShaft(int x, int z) {
    return x >= -11 && x <= -1 && z >= -10 && z <= 0;
  }

  private static void table(ServerLevel l, BlockPos base, int x0, int z0, int x1, int z1) {
    for (int x = x0; x <= x1; x++)
      for (int z = z0; z <= z1; z++)
        put(l, base.offset(x, 0, z), FurnitureRegistry.OSSUARY_TABLE.get());
  }

  private static void chair(ServerLevel l, BlockPos p, Direction d) {
    face(l, p, FurnitureRegistry.OSSUARY_CHAIR.get(), d);
  }

  private static void stand(ServerLevel l, BlockPos p, Direction d) {
    face(l, p, FurnitureRegistry.OSSUARY_LECTERN.get(), d);
    if (l.getBlockEntity(p) instanceof LecternBlockEntity e) {
      e.setBook(new ItemStack(Items.WRITABLE_BOOK));
      l.setBlock(p, l.getBlockState(p).setValue(LecternBlock.HAS_BOOK, true), 3);
    }
  }

  private static void bookcase(ServerLevel l, BlockPos p, Direction d, boolean archive) {
    face(
        l,
        p,
        archive
            ? FurnitureRegistry.OSSUARY_ARCHIVE.get()
            : FurnitureRegistry.OSSUARY_BOOKCASE.get(),
        d);
    if (archive && l.getBlockEntity(p) instanceof Container c) {
      c.setItem(0, new ItemStack(Items.BOOK));
      c.setItem(4, new ItemStack(Items.WRITABLE_BOOK));
    }
  }

  /** Retired vanilla furniture is replaced once; stored items are carried to the new cabinets. */
  private static List<ItemStack> clearOldFurniture(ServerLevel l, BlockPos floor) {
    var contents = new ArrayList<ItemStack>();
    var retired =
        Set.of(
            Blocks.BOOKSHELF,
            Blocks.LECTERN,
            Blocks.DARK_OAK_FENCE,
            Blocks.DARK_OAK_SLAB,
            Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_TRAPDOOR,
            Blocks.RED_WOOL,
            Blocks.ENCHANTING_TABLE,
            Blocks.CHEST,
            Blocks.BARREL,
            Blocks.RED_BED,
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.FLOWER_POT);
    for (int x = -12; x <= 12; x++)
      for (int z = -12; z <= 12; z++)
        for (int y = 0; y <= 7; y++) {
          if (stairShaft(x, z)) continue;
          var p = floor.offset(x, y, z);
          var state = l.getBlockState(p);
          if (!retired.contains(state.getBlock())) continue;
          if (l.getBlockEntity(p) instanceof Container c) {
            for (int slot = 0; slot < c.getContainerSize(); slot++)
              if (!c.getItem(slot).isEmpty()) contents.add(c.getItem(slot).copy());
            c.clearContent();
          }
          l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    return contents;
  }

  private static void cabinet(ServerLevel l, BlockPos p, Direction d, List<ItemStack> contents) {
    face(l, p, FurnitureRegistry.OSSUARY_CABINET.get(), d);
    if (l.getBlockEntity(p) instanceof Container c) {
      for (int slot = 0; slot < c.getContainerSize() && !contents.isEmpty(); slot++)
        c.setItem(slot, contents.removeFirst());
    }
  }

  public static void library(ServerLevel l, BlockPos surface) {
    var floor = surface.above(24);
    var contents = clearOldFurniture(l, floor);
    // Old floating desk lamp and its repair chain must leave with the old desk.
    for (int y = 2; y <= 10; y++) {
      var p = floor.offset(3, y, 3);
      var b = l.getBlockState(p).getBlock();
      if (b == Blocks.LANTERN || b == Blocks.CHAIN) l.removeBlock(p, false);
    }
    table(l, floor, -2, 2, 2, 3);
    chair(l, floor.offset(0, 0, 5), Direction.NORTH);
    stand(l, floor.offset(3, 0, 5), Direction.SOUTH);
    put(l, floor.offset(-1, 1, 2), Blocks.LANTERN);
    // Discrete alcoves rather than walls buried behind a grid of shelves.
    for (int x = -9; x <= -5; x++)
      for (int y = 0; y < 2; y++)
        bookcase(l, floor.offset(x, y, 11), Direction.NORTH, x % 2 == 0 && y == 0);
    for (int z = -7; z <= -3; z++)
      for (int y = 0; y < 2; y++)
        bookcase(l, floor.offset(11, y, z), Direction.WEST, z % 2 == 0 && y == 0);
    for (int x : new int[] {-10, -4})
      for (int y = 0; y < 3; y++) put(l, floor.offset(x, y, 11), Blocks.BONE_BLOCK);
    for (int x = -10; x <= -4; x++) put(l, floor.offset(x, 3, 11), Blocks.POLISHED_BLACKSTONE_SLAB);
    table(l, floor, -8, 6, -7, 7);
    chair(l, floor.offset(-6, 0, 7), Direction.WEST);
    put(l, floor.offset(-8, 1, 6), Blocks.LANTERN);
    // Fifteen tagged sources, one block of clear air between every source and the table.
    var enchant = enchantingTable(surface);
    put(l, enchant, Blocks.ENCHANTING_TABLE);
    for (int x = -2; x <= 2; x++) bookcase(l, enchant.offset(x, 0, 2), Direction.NORTH, false);
    for (int z = -2; z < 2; z++) {
      bookcase(l, enchant.offset(-2, 0, z), Direction.EAST, false);
      bookcase(l, enchant.offset(2, 0, z), Direction.WEST, false);
    }
    for (int x : new int[] {-1, 1}) bookcase(l, enchant.offset(x, 0, -2), Direction.SOUTH, false);
    cabinet(l, floor.offset(2, 0, 10), Direction.NORTH, contents);
    cabinet(l, floor.offset(3, 0, 10), Direction.NORTH, contents);
    for (var stack : contents)
      Containers.dropItemStack(l, floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 9.5, stack);
    // This is real enchantment geometry, not merely an ornamental bookshelf count.
    long power =
        EnchantingTableBlock.BOOKSHELF_OFFSETS.stream()
            .filter(o -> EnchantingTableBlock.isValidBookShelf(l, enchant, o))
            .count();
    if (power < 15)
      throw new IllegalStateException(
          "Estate library has only " + power + " valid enchanting shelves");
    var state = CoreTestState.get(l);
    state.denFurnitureVersion = Math.max(1, state.denFurnitureVersion);
    state.setDirty();
  }
}
