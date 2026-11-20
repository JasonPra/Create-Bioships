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

  @GameTest(template = "test_empty", batch = "estate_furniture", timeoutTicks = 40)
  public static void reading_stand_preserves_native_book_storage_and_custom_type(GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 2, 2));
    l.setBlock(
        p,
        FurnitureRegistry.OSSUARY_LECTERN
            .get()
            .defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true),
        3);
    var stand = (net.minecraft.world.level.block.entity.LecternBlockEntity) l.getBlockEntity(p);
    h.assertTrue(
        stand != null && stand.getType() == FurnitureRegistry.READING_STAND_ENTITY.get(),
        "Reading stand has wrong entity type");
    stand.setBook(
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK));
    var loaded =
        net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            p,
            l.getBlockState(p),
            stand.saveWithFullMetadata(l.registryAccess()),
            l.registryAccess());
    h.assertTrue(
        loaded instanceof net.minecraft.world.level.block.entity.LecternBlockEntity restored
            && restored.hasBook()
            && restored.getType() == FurnitureRegistry.READING_STAND_ENTITY.get(),
        "Reading stand lost book or type on reload");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "estate_furniture", timeoutTicks = 40)
  public static void chair_uses_native_seat_and_dye_does_not_replace_it(GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 2, 2));
    l.setBlock(p, FurnitureRegistry.OSSUARY_CHAIR.get().defaultBlockState(), 3);
    var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ChairTester");
    var player =
        new net.minecraft.server.level.ServerPlayer(
            l.getServer(),
            l,
            profile,
            net.minecraft.server.level.ClientInformation.createDefault());
    player.connection = new net.neoforged.neoforge.common.util.FakePlayer(l, profile).connection;
    var dye = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLUE_DYE);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dye);
    player.gameMode.useItemOn(
        player,
        l,
        dye,
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(p), Direction.SOUTH, p, false));
    h.assertTrue(
        player.getVehicle()
            instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity,
        "Chair did not seat player");
    h.assertTrue(
        l.getBlockState(p).is(FurnitureRegistry.OSSUARY_CHAIR.get()), "Dye replaced custom chair");
    player.stopRiding();
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "estate_furniture", timeoutTicks = 40)
  public static void archive_and_cabinet_keep_native_inventories_on_reload(GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(2, 2, 2));
    l.setBlock(
        p,
        FurnitureRegistry.OSSUARY_ARCHIVE
            .get()
            .defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH),
        3);
    var archive =
        (net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity) l.getBlockEntity(p);
    archive.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK));
    h.assertTrue(
        l.getBlockState(p)
            .getValue(
                net.minecraft.world.level.block.ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES
                    .getFirst()),
        "Archive did not display occupied native slot");
    var restored =
        net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            p,
            l.getBlockState(p),
            archive.saveWithFullMetadata(l.registryAccess()),
            l.registryAccess());
    h.assertTrue(
        restored instanceof net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity a
            && a.getItem(0).is(net.minecraft.world.item.Items.BOOK)
            && a.getType() == FurnitureRegistry.ARCHIVE_ENTITY.get(),
        "Archive lost saved book or custom type");
    archive.removeItem(0, 1);
    h.assertTrue(
        !l.getBlockState(p)
            .getValue(
                net.minecraft.world.level.block.ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES
                    .getFirst()),
        "Removing book left occupied slot");
    l.setBlock(p.east(), FurnitureRegistry.OSSUARY_CABINET.get().defaultBlockState(), 3);
    var cabinet =
        (net.minecraft.world.level.block.entity.BarrelBlockEntity) l.getBlockEntity(p.east());
    cabinet.setItem(
        26, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 17));
    var saved =
        net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            p.east(),
            l.getBlockState(p.east()),
            cabinet.saveWithFullMetadata(l.registryAccess()),
            l.registryAccess());
    h.assertTrue(
        saved instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity c
            && c.getContainerSize() == 27
            && c.getItem(26).getCount() == 17
            && c.getType() == FurnitureRegistry.CABINET_ENTITY.get(),
        "Cabinet lost saved inventory or custom type");
    h.succeed();
  }
}
