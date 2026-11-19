package org.Azkerash.create_bioships.core;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.stomach.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class StomachDigestionTests {
  @GameTest(template = "test_empty", batch = "passive_stomach")
  public static void isolated_stomach_digests_and_preserves_its_contents(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    var state = ComponentRegistry.STOMACH.get().defaultBlockState();
    level.setBlock(pos, state, 3);
    var body =
        dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
            level,
            pos,
            java.util.List.of(pos),
            new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos, pos));
    SubLevelData.living(body, true);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    pos = body.getPlot().getCenterBlock();
    var stomach = (StomachBlockEntity) level.getBlockEntity(pos);
    int acid = stomach.acid();
    stomach.advance();
    helper.assertTrue(stomach.acid() == acid + 1, "Disconnected stomach did not produce acid");
    helper.assertTrue(
        !stomach.canPlaceItem(0, new ItemStack(Items.PORKCHOP)),
        "Direct stomach insertion allowed");
    helper.assertTrue(
        stomach.enqueue(new ItemStack(Items.PORKCHOP), false).isEmpty(),
        "Transport input refused pork");
    for (int i = 0; i < 1000 && stomach.progress() == 0; i++) stomach.advance();
    helper.assertTrue(stomach.duration() > 0, "No native Biomancy recipe started");
    var saved = stomach.saveWithFullMetadata(level.registryAccess());
    var restored =
        (StomachBlockEntity) BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
    restored.setLevel(level);
    helper.assertTrue(
        restored.acid() == stomach.acid()
            && restored.progress() == stomach.progress()
            && restored.queued() == 1,
        "Save/load lost active digestion");
    for (int i = 0; i < 10000 && restored.queued() > 0; i++) restored.advance();
    helper.assertTrue(restored.queued() == 0, "Disconnected digestion stalled");
    int paste = 0, components = 0;
    for (int i = 9; i < 27; i++) {
      var stack = restored.getItem(i);
      if (stack.is(StomachBlockEntity.paste())) paste += stack.getCount();
      else components += stack.getCount();
    }
    helper.assertTrue(
        paste == 1 && components > 0, "Meal did not produce components and nutrient paste");
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "StomachMenu"));
    var menu = new StomachMenu(1, player.getInventory(), restored);
    for (int i = 0; i < 27; i++)
      helper.assertTrue(
          !menu.getSlot(i).mayPlace(new ItemStack(Items.PORKCHOP))
              && !menu.getSlot(i).mayPickup(player),
          "Stomach GUI changed internal inventory");
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "passive_stomach")
  public static void full_outputs_preserve_input_and_do_not_reroll_saved_meals(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(1, 2, 1));
    var state = ComponentRegistry.STOMACH.get().defaultBlockState();
    level.setBlock(pos, state, 3);
    var body =
        dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
            level,
            pos,
            java.util.List.of(pos),
            new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos, pos));
    SubLevelData.living(body, true);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    pos = body.getPlot().getCenterBlock();
    var stomach = (StomachBlockEntity) level.getBlockEntity(pos);
    for (int slot = 9; slot < 27; slot++) stomach.setItem(slot, new ItemStack(Items.STONE, 64));
    stomach.enqueue(new ItemStack(Items.PORKCHOP), false);
    for (int tick = 0; tick < 500; tick++) stomach.advance();
    helper.assertTrue(
        stomach.queued() == 1 && stomach.progress() == stomach.duration(),
        "Full output storage lost input or failed to pause");
    var saved = stomach.saveWithFullMetadata(level.registryAccess());
    var restored =
        (StomachBlockEntity) BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
    restored.setLevel(level);
    restored.setItem(9, ItemStack.EMPTY);
    restored.advance();
    helper.assertTrue(
        restored.queued() == 1 && restored.getItem(9).isEmpty(),
        "Partial output insertion was not rolled back");
    helper.assertTrue(
        saved
            .getCompound("Pending")
            .equals(restored.saveWithFullMetadata(level.registryAccess()).getCompound("Pending")),
        "Blocked meal rerolled outputs after loading");
    for (int slot = 9; slot < 27; slot++) restored.setItem(slot, ItemStack.EMPTY);
    restored.advance();
    helper.assertTrue(restored.queued() == 0, "Freed storage did not finish the pending meal");
    helper.succeed();
  }

  @GameTest(template = "test_empty", batch = "paste_conversion")
  public static void three_paste_across_slots_restore_one_hunger_after_eating(GameTestHelper h) {
    var level = h.getLevel();
    var cell = h.absolutePos(new BlockPos(1, 2, 1));
    level.setBlock(cell, ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    var body =
        dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
            level,
            cell,
            java.util.List.of(cell),
            new dev.ryanhcode.sable.companion.math.BoundingBox3i(cell, cell));
    SubLevelData.living(body, true);
    SubLevelData.hungerPaused(body, true);
    SubLevelData.hunger(body, 2);
    var stomach = (StomachBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    stomach.setItem(9, new ItemStack(StomachBlockEntity.paste()));
    stomach.setItem(10, new ItemStack(StomachBlockEntity.paste()));
    for (int i = 0; i < StomachBlockEntity.EAT_TICKS * 2; i++) stomach.advance();
    h.assertTrue(
        SubLevelData.hunger(body) == 2
            && stomach.getItem(9).getCount() == 1
            && stomach.getItem(10).getCount() == 1,
        "An incomplete paste portion was consumed");
    stomach.setItem(11, new ItemStack(StomachBlockEntity.paste()));
    for (int i = 0; i < StomachBlockEntity.EAT_TICKS - 1; i++) stomach.advance();
    h.assertTrue(
        SubLevelData.hunger(body) == 2, "Paste restored hunger before the eating cycle finished");
    stomach.advance();
    h.assertTrue(
        SubLevelData.hunger(body) == 3
            && stomach.getItem(9).isEmpty()
            && stomach.getItem(10).isEmpty()
            && stomach.getItem(11).isEmpty(),
        "Three paste across output slots did not restore exactly one hunger");
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    stomach.setItem(9, new ItemStack(StomachBlockEntity.paste(), 6));
    for (int i = 0; i < StomachBlockEntity.EAT_TICKS * 2; i++) stomach.advance();
    h.assertTrue(stomach.getItem(9).getCount() == 6, "Full hunger wasted paste");
    System.out.println("PASTE_CONVERSION_PASS ratio=3:1 cycle=32 partial_portion_safe=true");
    h.succeed();
  }
}
