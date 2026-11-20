package org.azkerash.createbioships.development.testing;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import org.azkerash.createbioships.furniture.FurnitureRegistry;

/** Authored furniture layouts, independent of the vault structure and its living subjects. */
public final class EstateInteriors {
  private EstateInteriors() {}

  public static BlockPos enchantingTable(BlockPos surface) {
    return surface.offset(7, 24, 7);
  }

  private static void put(ServerLevel l, BlockPos p, Block b) {
    l.setBlock(p, b.defaultBlockState(), Block.UPDATE_ALL);
  }

  private static void face(ServerLevel l, BlockPos p, Block b, Direction d) {
    var state = b.defaultBlockState();
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
      state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    } else if (state.hasProperty(BlockStateProperties.FACING)) {
      state = state.setValue(BlockStateProperties.FACING, d);
    }
    l.setBlock(p, state, Block.UPDATE_ALL);
  }

  private static boolean stairShaft(int x, int z) {
    return x >= -11 && x <= -1 && z >= -10 && z <= 0;
  }

  private static void table(ServerLevel l, BlockPos base, int x0, int z0, int x1, int z1) {
    for (int x = x0; x <= x1; x++) {
      for (int z = z0; z <= z1; z++) {
        put(l, base.offset(x, 0, z), FurnitureRegistry.OSSUARY_TABLE.get());
      }
    }
  }

  private static void chair(ServerLevel l, BlockPos p, Direction d) {
    face(l, p, FurnitureRegistry.OSSUARY_CHAIR.get(), d);
  }

  private static void stand(ServerLevel l, BlockPos p, Direction d) {
    face(l, p, FurnitureRegistry.OSSUARY_LECTERN.get(), d);
    if (l.getBlockEntity(p) instanceof LecternBlockEntity e) {
      e.setBook(new ItemStack(Items.WRITABLE_BOOK));
      l.setBlock(p, l.getBlockState(p).setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_ALL);
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
            Blocks.FLOWER_POT,
            Blocks.SMOOTH_STONE,
            Blocks.FURNACE,
            Blocks.SMOKER,
            Blocks.CAULDRON,
            Blocks.CRAFTING_TABLE);
    for (int x = -12; x <= 12; x++) {
      for (int z = -12; z <= 12; z++) {
        for (int y = 0; y <= 7; y++) {
          if (stairShaft(x, z)) {
            continue;
          }
          var p = floor.offset(x, y, z);
          var state = l.getBlockState(p);
          if (!retired.contains(state.getBlock())) {
            continue;
          }
          if (l.getBlockEntity(p) instanceof Container c) {
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
              if (!c.getItem(slot).isEmpty()) {
                contents.add(c.getItem(slot).copy());
              }
            }
            c.clearContent();
          }
          l.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
      }
    }
    return contents;
  }

  private static void cabinet(ServerLevel l, BlockPos p, Direction d, List<ItemStack> contents) {
    face(l, p, FurnitureRegistry.OSSUARY_CABINET.get(), d);
    if (l.getBlockEntity(p) instanceof Container c) {
      for (int slot = 0; slot < c.getContainerSize() && !contents.isEmpty(); slot++) {
        c.setItem(slot, contents.removeFirst());
      }
    }
  }

  public static void library(ServerLevel l, BlockPos surface) {
    var floor = surface.above(24);
    var contents = clearOldFurniture(l, floor);
    // Old floating desk lamp and its repair chain must leave with the old desk.
    for (int y = 2; y <= 10; y++) {
      var p = floor.offset(3, y, 3);
      var b = l.getBlockState(p).getBlock();
      if (b == Blocks.LANTERN || b == Blocks.CHAIN) {
        l.removeBlock(p, false);
      }
    }
    table(l, floor, -2, 2, 2, 3);
    chair(l, floor.offset(0, 0, 4), Direction.NORTH);
    stand(l, floor.offset(3, 0, 5), Direction.SOUTH);
    put(l, floor.offset(-1, 1, 2), Blocks.LANTERN);
    // Discrete alcoves rather than walls buried behind a grid of shelves.
    for (int x = -9; x <= -5; x++) {
      for (int y = 0; y < 2; y++) {
        bookcase(l, floor.offset(x, y, 11), Direction.NORTH, x % 2 == 0 && y == 0);
      }
    }
    for (int z = -7; z <= -3; z++) {
      for (int y = 0; y < 2; y++) {
        bookcase(l, floor.offset(11, y, z), Direction.WEST, z % 2 == 0 && y == 0);
      }
    }
    for (int x : new int[] {-10, -4}) {
      for (int y = 0; y < 3; y++) {
        put(l, floor.offset(x, y, 11), Blocks.BONE_BLOCK);
      }
    }
    for (int x = -10; x <= -4; x++) {
      put(l, floor.offset(x, 3, 11), Blocks.POLISHED_BLACKSTONE_SLAB);
    }
    table(l, floor, -8, 6, -7, 7);
    chair(l, floor.offset(-6, 0, 7), Direction.WEST);
    put(l, floor.offset(-8, 1, 6), Blocks.LANTERN);
    // Fifteen tagged sources, one block of clear air between every source and the table.
    var enchant = enchantingTable(surface);
    put(l, enchant, Blocks.ENCHANTING_TABLE);
    for (int x = -2; x <= 2; x++) {
      bookcase(l, enchant.offset(x, 0, 2), Direction.NORTH, false);
    }
    for (int z = -2; z < 2; z++) {
      bookcase(l, enchant.offset(-2, 0, z), Direction.EAST, false);
      bookcase(l, enchant.offset(2, 0, z), Direction.WEST, false);
    }
    for (int x : new int[] {-1, 1}) {
      bookcase(l, enchant.offset(x, 0, -2), Direction.SOUTH, false);
    }
    cabinet(l, floor.offset(2, 0, 10), Direction.NORTH, contents);
    cabinet(l, floor.offset(3, 0, 10), Direction.NORTH, contents);
    for (var stack : contents) {
      Containers.dropItemStack(l, floor.getX() + 2.5, floor.getY() + 1, floor.getZ() + 9.5, stack);
    }
    // This is real enchantment geometry, not merely an ornamental bookshelf count.
    long power =
        EnchantingTableBlock.BOOKSHELF_OFFSETS.stream()
            .filter(o -> EnchantingTableBlock.isValidBookShelf(l, enchant, o))
            .count();
    if (power < 15) {
      throw new IllegalStateException(
          "Estate library has only " + power + " valid enchanting shelves");
    }
    var state = CoreTestState.get(l);
    state.setDenFurnitureVersion(Math.max(1, state.getDenFurnitureVersion()));
    state.setDirty();
  }

  private static void bed(ServerLevel l, BlockPos foot, Direction facing) {
    var b = FurnitureRegistry.OSSUARY_BED.get();
    var state = b.defaultBlockState().setValue(BedBlock.FACING, facing);
    l.setBlock(foot, state, Block.UPDATE_CLIENTS);
    b.setPlacedBy(l, foot, state, null, ItemStack.EMPTY);
  }

  private static void pendant(ServerLevel l, BlockPos floor, int x, int z, int lampHeight) {
    int ceiling = 11;
    for (int y = lampHeight + 1; y < ceiling; y++) {
      put(l, floor.offset(x, y, z), Blocks.CHAIN);
    }
    l.setBlock(
        floor.offset(x, lampHeight, z),
        Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
        Block.UPDATE_ALL);
  }

  public static void rooms(ServerLevel l, BlockPos surface) {
    var kitchen = surface.above(12);
    var contents = clearOldFurniture(l, kitchen);
    // Fitted functional cabinets, with real cooking blocks supported by the counter beneath them.
    for (int z = 2; z <= 10; z++) {
      cabinet(l, kitchen.offset(-10, 0, z), Direction.EAST, contents);
    }
    face(l, kitchen.offset(-10, 1, 2), Blocks.FURNACE, Direction.EAST);
    face(l, kitchen.offset(-10, 1, 5), Blocks.FURNACE, Direction.EAST);
    face(l, kitchen.offset(-10, 1, 8), Blocks.SMOKER, Direction.EAST);
    put(l, kitchen.offset(-8, 0, 10), Blocks.CRAFTING_TABLE);
    put(l, kitchen.offset(-8, 0, 8), Blocks.WATER_CAULDRON);
    table(l, kitchen, -7, 4, -6, 7);
    put(l, kitchen.offset(-6, 1, 4), Blocks.LANTERN);
    table(l, kitchen, 0, 3, 1, 7);
    for (int z : new int[] {3, 5, 7}) {
      chair(l, kitchen.offset(-1, 0, z), Direction.EAST);
      chair(l, kitchen.offset(2, 0, z), Direction.WEST);
    }
    pendant(l, kitchen, 0, 5, 5);
    // Guest seating around a low-volume table, with clear circulation around the stair doorway.
    table(l, kitchen, 8, -4, 9, -3);
    for (int x : new int[] {8, 9}) {
      chair(l, kitchen.offset(x, 0, -5), Direction.SOUTH);
      chair(l, kitchen.offset(x, 0, -2), Direction.NORTH);
    }
    for (int y = 0; y < 2; y++) {
      bookcase(l, kitchen.offset(11, y, -7), Direction.WEST, y == 0);
    }
    put(l, kitchen.offset(8, 1, -4), Blocks.LANTERN);
    cabinet(l, kitchen.offset(10, 0, 9), Direction.NORTH, contents);
    for (var item : contents) {
      Containers.dropItemStack(
          l, kitchen.getX() - 9.5, kitchen.getY() + 1, kitchen.getZ() + 9.5, item);
    }

    var bedroom = surface.above(36);
    contents = clearOldFurniture(l, bedroom);
    bed(l, bedroom.offset(-1, 0, 7), Direction.NORTH);
    bed(l, bedroom.offset(0, 0, 7), Direction.NORTH);
    for (int x : new int[] {-2, 1}) {
      cabinet(l, bedroom.offset(x, 0, 6), Direction.SOUTH, contents);
      put(l, bedroom.offset(x, 1, 6), Blocks.LANTERN);
    }
    for (int z = 7; z <= 10; z++) {
      for (int y = 0; y < 2; y++) {
        cabinet(l, bedroom.offset(10, y, z), Direction.WEST, contents);
      }
    }
    table(l, bedroom, 6, 3, 8, 4);
    chair(l, bedroom.offset(7, 0, 5), Direction.NORTH);
    stand(l, bedroom.offset(9, 0, 5), Direction.SOUTH);
    put(l, bedroom.offset(6, 1, 3), Blocks.LANTERN);
    for (int z = -6; z <= -4; z++) {
      bookcase(l, bedroom.offset(11, 0, z), Direction.WEST, z == -5);
    }
    chair(l, bedroom.offset(9, 0, -4), Direction.EAST);
    table(l, bedroom, 7, -5, 7, -4);
    put(l, bedroom.offset(7, 1, -5), Blocks.LANTERN);
    for (var item : contents) {
      Containers.dropItemStack(
          l, bedroom.getX() + 9.5, bedroom.getY() + 1, bedroom.getZ() + 8.5, item);
    }
    // Entry reading stands belong on the floor, with their own supported tops.
    for (int x : new int[] {-5, 5}) {
      stand(l, surface.offset(x, 0, -20), Direction.SOUTH);
      var oldSkull = surface.offset(x, 1, -25);
      if (l.getBlockState(oldSkull).is(Blocks.SKELETON_SKULL)) {
        l.removeBlock(oldSkull, false);
      }
    }
    // Replace decorative vanilla book towers in the biological workshop, keeping its machines.
    var vault = NecromancerDenFixture.base(l);
    for (int x : new int[] {22, 28, 34, 39}) {
      for (int y = 1; y <= 2; y++) {
        bookcase(l, vault.offset(x, y, -39), Direction.SOUTH, y == 1 && x % 2 == 0);
      }
    }
    for (int z : new int[] {-27, -14, -1}) {
      var foot = vault.offset(-39, 0, z + 1);
      l.removeBlock(foot, false);
      l.removeBlock(foot.north(), false);
      bed(l, foot, Direction.NORTH);
    }
    var state = CoreTestState.get(l);
    state.setDenFurnitureVersion(2);
    state.setDirty();
  }

  private static void shiftCabinet(ServerLevel l, BlockPos from, BlockPos to) {
    var entity = l.getBlockEntity(from);
    if (entity == null || !l.getBlockState(from).is(FurnitureRegistry.OSSUARY_CABINET.get())) {
      return;
    }
    var saved = entity.saveWithoutMetadata(l.registryAccess());
    var state = l.getBlockState(from);
    if (entity instanceof Container c) {
      c.clearContent();
    }
    l.removeBlock(from, false);
    l.setBlock(to, state, Block.UPDATE_ALL);
    var target = l.getBlockEntity(to);
    if (target != null) {
      target.loadWithComponents(saved, l.registryAccess());
      target.setChanged();
    }
    if (l.getBlockState(from.above()).is(Blocks.LANTERN)) {
      l.removeBlock(from.above(), false);
      put(l, to.above(), Blocks.LANTERN);
    }
  }

  public static void polish(ServerLevel l, BlockPos surface) {
    for (int floor : new int[] {12, 24, 36}) {
      for (int x = -12; x <= 12; x++) {
        for (int z = -12; z <= 12; z++) {
          var p = surface.offset(x, floor, z);
          if (l.getBlockState(p).is(Blocks.PURPLE_CARPET)) {
            l.removeBlock(p, false);
          }
          if (l.getBlockState(p.below()).is(Blocks.SHROOMLIGHT)) {
            put(l, p.below(), Blocks.DARK_OAK_PLANKS);
          }
        }
      }
      for (var face : Direction.Plane.HORIZONTAL) {
        for (int side : new int[] {-5, 5}) {
          var tangent = face.getClockWise();
          int x = face.getStepX() * 13 + tangent.getStepX() * side,
              z = face.getStepZ() * 13 + tangent.getStepZ() * side;
          var hook = surface.offset(x, floor + 4, z);
          var outer = hook.relative(face.getOpposite());
          l.setBlock(hook, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), Block.UPDATE_ALL);
          l.setBlock(outer, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), Block.UPDATE_ALL);
          for (var p : new BlockPos[] {hook, outer}) {
            l.setBlock(
                p, Block.updateFromNeighbourShapes(l.getBlockState(p), l, p), Block.UPDATE_CLIENTS);
          }
          l.setBlock(
              outer.below(),
              Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
              Block.UPDATE_ALL);
        }
      }
      for (int[] lamp : new int[][] {{0, 5}, {7, -4}, {-6, 7}}) {
        pendant(l, surface.above(floor), lamp[0], lamp[1], 6);
      }
    }
    var old = surface.offset(0, 24, 5);
    if (l.getBlockState(old).is(FurnitureRegistry.OSSUARY_CHAIR.get())) {
      l.removeBlock(old, false);
    }
    chair(l, surface.offset(0, 24, 4), Direction.NORTH);
    old = surface.offset(7, 36, 6);
    if (l.getBlockState(old).is(FurnitureRegistry.OSSUARY_CHAIR.get())) {
      l.removeBlock(old, false);
    }
    chair(l, surface.offset(7, 36, 5), Direction.NORTH);
    for (int x : new int[] {7, 10}) {
      for (int z : new int[] {-5, -1}) {
        if (l.getBlockState(surface.offset(x, 12, z)).is(FurnitureRegistry.OSSUARY_CHAIR.get())) {
          l.removeBlock(surface.offset(x, 12, z), false);
        }
      }
    }
    for (int x : new int[] {8, 9}) {
      chair(l, surface.offset(x, 12, -5), Direction.SOUTH);
      chair(l, surface.offset(x, 12, -2), Direction.NORTH);
    }
    shiftCabinet(l, surface.offset(-3, 36, 6), surface.offset(-2, 36, 6));
    shiftCabinet(l, surface.offset(2, 36, 6), surface.offset(1, 36, 6));
    var state = CoreTestState.get(l);
    // The old connector road placed a street-lamp post and hook directly across the entrance.
    for (int z = -38; z <= -36; z++) {
      for (int y = 0; y <= 4; y++) {
        var p = surface.offset(0, y, z);
        var b = l.getBlockState(p).getBlock();
        if (b == Blocks.DEEPSLATE_BRICK_WALL || b == Blocks.LANTERN) {
          l.removeBlock(p, false);
        }
      }
    }
    state.setDenFurnitureVersion(4);
    state.setDirty();
  }
}
