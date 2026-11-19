package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.stomach.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class StomachPasteSlotTests {
  @GameTest(template = "test_empty", batch = "reserved_stomach_paste")
  public static void reserved_cell_filters_automation_and_player_moves_without_losing_legacy_items(
      GameTestHelper h) {
    var level = h.getLevel();
    var origin = h.absolutePos(new BlockPos(1, 3, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(origin, origin.east()),
            new BoundingBox3i(origin, origin.east()));
    CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
    SubLevelData.hungerPaused(body, true);
    var stomach = (StomachBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock().east());
    int reserved = StomachBlockEntity.PASTE_SLOT;
    h.assertTrue(
        stomach.getContainerSize() == 27
            && stomach.canPlaceItem(reserved, new ItemStack(StomachBlockEntity.paste()))
            && !stomach.canPlaceItem(reserved, new ItemStack(Items.BEEF)),
        "Reserved slot changed inventory size or accepts other items");
    for (int i = 9; i < reserved; i++) stomach.setItem(i, new ItemStack(Items.BONE, 64));
    h.assertTrue(
        stomach.insert(new ItemStack(StomachBlockEntity.paste(), 32), 9, 27, true).isEmpty()
            && stomach.getItem(reserved).isEmpty(),
        "Paste simulation mutated storage or could not use reserved cell");
    h.assertTrue(
        stomach.insert(new ItemStack(StomachBlockEntity.paste(), 32), 9, 27, false).isEmpty()
            && stomach.getItem(reserved).getCount() == 32,
        "Full component outputs blocked the reserved paste cell");
    h.assertTrue(
        stomach.insert(new ItemStack(Items.BONE), reserved, 27, false).getCount() == 1,
        "Non-paste was admitted into reserved cell");
    stomach.clearContent();
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "PasteSlotTest"));
    var menu = new StomachMenu(1, player.getInventory(), stomach);
    player.getInventory().setItem(9, new ItemStack(StomachBlockEntity.paste(), 32));
    h.assertTrue(
        !menu.quickMoveStack(player, 27).isEmpty() && stomach.getItem(reserved).getCount() == 32,
        "Player shift-click could not feed reserved paste cell");
    player.getInventory().setItem(10, new ItemStack(Items.BEEF));
    h.assertTrue(
        menu.quickMoveStack(player, 28).isEmpty() && stomach.getItem(0).isEmpty(),
        "Paste input allowed food to bypass the mouth");
    menu.clicked(reserved, 0, ClickType.PICKUP, player);
    h.assertTrue(
        stomach.getItem(reserved).isEmpty() && menu.getCarried().getCount() == 32,
        "Reserved paste could not be retrieved normally");
    for (int i = 9; i < 27; i++) stomach.setItem(i, new ItemStack(Items.BONE, 64));
    stomach.normalizePasteSlot();
    h.assertTrue(
        stomach.getItem(reserved).getCount() == 64,
        "Migration discarded old contents when outputs were full");
    var removed = stomach.removeItem(9, 64);
    stomach.normalizePasteSlot();
    h.assertTrue(
        stomach.getItem(reserved).isEmpty()
            && stomach.getItem(9).getCount() == 64
            && removed.getCount() == 64,
        "Legacy contents were not preserved and relocated after room became available");
    h.succeed();
  }
}
