package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.components.logic.mouth.*;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class FeedingTrustTests {
  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "feeding_trust")
  public static void hand_menu_connected_sac_and_fractional_trust(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = helper.absolutePos(new BlockPos(1, 3, 1));
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), 3);
    level.setBlock(origin.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), 3);
    level.setBlock(origin.east(3), ComponentRegistry.MOUTH.get().defaultBlockState(), 3);
    var sacBlock =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.parse("biomancy:storage_sac"));
    level.setBlock(origin.east(4), sacBlock.defaultBlockState(), 3);
    level.setBlock(origin.east(6), sacBlock.defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            origin,
            List.of(
                origin,
                origin.east(),
                origin.east(2),
                origin.east(3),
                origin.east(4),
                origin.east(6)),
            new BoundingBox3i(origin, origin.east(6)));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(level, core);
    SubLevelData.hungerPaused(body, true);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FeedingTest"));
    var mouth = (MouthBlockEntity) level.getBlockEntity(core.east(3));
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 5));
    var hit =
        new BlockHitResult(Vec3.atCenterOf(core.east(3)), Direction.NORTH, core.east(3), false);
    player.gameMode.useItemOn(
        player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(
        Math.abs(SubLevelData.trust(body) - .1) < 1e-8,
        "Hand feeding did not retain fractional trust");
    var menu = new MouthMenu(1, player.getInventory(), mouth);
    player.getInventory().setItem(9, new ItemStack(Items.PORKCHOP, 4));
    menu.clicked(9, 0, ClickType.QUICK_MOVE, player);
    helper.assertTrue(
        Math.abs(SubLevelData.trust(body) - .5) < 1e-8, "GUI stack feeding count incorrect");
    var sac =
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            level.getBlockEntity(core.east(4));
    var sacMenu =
        com.github.elenterius.biomancy.menu.StorageSacMenu.createServerMenu(
            2, player.getInventory(), sac);
    player.getInventory().setItem(9, new ItemStack(Items.BEEF, 3));
    int inventorySlot = -1;
    for (int i = 0; i < sacMenu.slots.size(); i++) {
      var slot = sacMenu.slots.get(i);
      if (slot.container == player.getInventory() && slot.getContainerSlot() == 9)
        inventorySlot = i;
    }
    helper.assertTrue(inventorySlot >= 0, "Native sac menu has no player inventory slot");
    sacMenu.clicked(inventorySlot, 0, ClickType.QUICK_MOVE, player);
    helper.assertTrue(
        Math.abs(SubLevelData.trust(body) - .8) < 1e-8, "Connected sac feeding count incorrect");
    var unconnected =
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            level.getBlockEntity(core.east(6));
    helper.assertTrue(
        FeedingTrust.snapshot(
                com.github.elenterius.biomancy.menu.StorageSacMenu.createServerMenu(
                    3, player.getInventory(), unconnected))
            == null,
        "Disconnected sac earned trust");
    double trust = SubLevelData.trust(body);
    mouth.feed(new ItemStack(Items.BEEF, 2), false);
    helper.assertTrue(SubLevelData.trust(body) == trust, "Autonomous feeding earned trust");
    SubLevelData.trust(body, 99.95);
    FeedingTrust.hand(player, core.east(3), new ItemStack(Items.BEEF), 1);
    helper.assertTrue(SubLevelData.trust(body) == 100, "Trust cap failed");
    SubLevelHealth.damage(body, 999, "test");
    SubLevelData.trust(body, 1);
    FeedingTrust.hand(player, core.east(3), new ItemStack(Items.BEEF), 1);
    helper.assertTrue(SubLevelData.trust(body) == 1, "Corpse feeding earned trust");
    helper.succeed();
  }
}
