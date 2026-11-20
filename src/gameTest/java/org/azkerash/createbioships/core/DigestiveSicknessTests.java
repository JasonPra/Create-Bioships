package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.mouth.*;
import org.azkerash.createbioships.components.logic.stomach.*;
import org.azkerash.createbioships.core.digestion.*;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class DigestiveSicknessTests {
  private record Fixture(
      ServerSubLevel body,
      MouthBlockEntity mouth,
      StomachBlockEntity stomach,
      com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity sac,
      net.neoforged.neoforge.common.util.FakePlayer player) {}

  private static Fixture fixture(GameTestHelper h) {
    var l = h.getLevel();
    var origin = h.absolutePos(new BlockPos(1, 3, 1));
    var cells =
        List.of(origin, origin.east(), origin.east(2), origin.east(3), origin.east(2).above());
    l.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(
        origin.east(), ComponentRegistry.STOMACH.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(
        origin.east(2), ComponentRegistry.INTESTINE.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(origin.east(3), ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(
        origin.east(2).above(),
        BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("biomancy:storage_sac"))
            .defaultBlockState(),
        Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, origin, cells, new BoundingBox3i(origin, origin.east(3).above()));
    var core = body.getPlot().getCenterBlock();
    CoreAssemblyRules.mark(l, core);
    DevelopmentLifeSupport.pause(body);
    SubLevelData.hunger(body, SubLevelData.maximumHunger(body));
    SubLevelData.trust(body, 20);
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SicknessTest"));
    return new Fixture(
        body,
        (MouthBlockEntity) l.getBlockEntity(core.east(3)),
        (StomachBlockEntity) l.getBlockEntity(core.east()),
        (com.github.elenterius.biomancy.block.storagesac.StorageSacBlockEntity)
            l.getBlockEntity(core.east(2).above()),
        player);
  }

  private static void hand(Fixture f, Item item) {
    f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    f.player.gameMode.useItemOn(
        f.player,
        f.player.serverLevel(),
        f.player.getMainHandItem(),
        InteractionHand.MAIN_HAND,
        new BlockHitResult(
            Vec3.atCenterOf(f.mouth.getBlockPos()), Direction.NORTH, f.mouth.getBlockPos(), false));
  }

  private static int sacSlot(AbstractContainerMenu menu, Fixture f, boolean player, Item item) {
    for (int i = 0; i < menu.slots.size(); i++) {
      var s = menu.slots.get(i);
      if (player
          ? s.container == f.player.getInventory() && s.getContainerSlot() == 9
          : s.container != f.player.getInventory() && s.getItem().is(item)) {
        return i;
      }
    }
    throw new IllegalStateException("Sac slot missing");
  }

  private static void trust(GameTestHelper h, Fixture f, double expected, String reason) {
    h.assertTrue(
        Math.abs(SubLevelData.trust(f.body) - expected) < 1e-8,
        reason + ": " + SubLevelData.trust(f.body));
  }

  @GameTest(template = "test_empty", batch = "paste_feeding_trust")
  public static void paste_hand_mouth_sac_and_reserved_slot_have_symmetric_trust(GameTestHelper h) {
    var f = fixture(h);
    var paste = StomachBlockEntity.paste();
    hand(f, paste);
    trust(h, f, 20.05, "Hand-fed paste did not earn its small reward");
    var mouth = new MouthMenu(1, f.player.getInventory(), f.mouth);
    mouth.clicked(0, 0, ClickType.PICKUP, f.player);
    trust(h, f, 20, "Taking hand-fed paste did not reverse reward");
    mouth.clicked(0, 0, ClickType.PICKUP, f.player);
    trust(h, f, 20.05, "Returning paste was counted incorrectly");
    mouth.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20, "Paste take/return farmed trust");
    f.mouth.feed(new ItemStack(paste), false);
    h.assertTrue(
        SubLevelDigestion.transfer(f.mouth) == 1
            && f.stomach.queued() == 0
            && f.stomach.getItem(StomachBlockEntity.PASTE_SLOT).is(paste),
        "Mouth paste was decomposed instead of routed into paste storage");
    trust(h, f, 20, "Automatic paste routing earned extra trust");
    f.player.getInventory().setItem(9, new ItemStack(paste, 4));
    mouth.clicked(9, 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20.2, "Mouth paste stack reward was incorrect");
    mouth.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20, "Mouth paste stack withdrawal was incorrect");
    var sac =
        com.github.elenterius.biomancy.menu.StorageSacMenu.createServerMenu(
            2, f.player.getInventory(), f.sac);
    f.player.getInventory().setItem(9, new ItemStack(paste, 8));
    sac.clicked(sacSlot(sac, f, true, paste), 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20.4, "Connected paste storage did not reward the deposited count");
    sac.clicked(sacSlot(sac, f, false, paste), 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20, "Sac paste withdrawal did not reverse its reward");
    f.stomach.setItem(StomachBlockEntity.PASTE_SLOT, ItemStack.EMPTY);
    var stomach = new StomachMenu(3, f.player.getInventory(), f.stomach);
    f.player.getInventory().setItem(9, new ItemStack(paste, 6));
    stomach.clicked(27, 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20.3, "Reserved paste slot deposit did not earn trust");
    stomach.clicked(StomachBlockEntity.PASTE_SLOT, 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 20, "Reserved paste slot withdrawal farmed trust");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "rotten_flesh_sickness")
  public static void rotten_food_penalizes_players_and_consumption_causes_saved_bounded_sickness(
      GameTestHelper h) {
    var f = fixture(h);
    hand(f, Items.ROTTEN_FLESH);
    trust(h, f, 18, "Rotten flesh received normal feeding trust");
    h.assertTrue(
        SubLevelSickness.remaining(f.body) == 0,
        "Merely queued food caused sickness before consumption");
    var mouth = new MouthMenu(1, f.player.getInventory(), f.mouth);
    mouth.clicked(0, 0, ClickType.PICKUP, f.player);
    trust(h, f, 18, "Taking rotten flesh back refunded its penalty");
    mouth.clicked(0, 0, ClickType.PICKUP, f.player);
    trust(h, f, 16, "Putting rotten flesh back did not penalize feeding again");
    var sac =
        com.github.elenterius.biomancy.menu.StorageSacMenu.createServerMenu(
            2, f.player.getInventory(), f.sac);
    f.player.getInventory().setItem(9, new ItemStack(Items.ROTTEN_FLESH, 2));
    sac.clicked(sacSlot(sac, f, true, Items.ROTTEN_FLESH), 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 12, "Connected rotten-food deposit was not penalized");
    sac.clicked(sacSlot(sac, f, false, Items.ROTTEN_FLESH), 0, ClickType.QUICK_MOVE, f.player);
    trust(h, f, 12, "Removing rotten-food stock granted trust");
    h.assertTrue(SubLevelDigestion.transfer(f.mouth) == 1, "Rotten meal did not reach stomach");
    for (int i = 0; i < 2000 && f.stomach.queued() > 0; i++) {
      f.stomach.advance();
    }
    h.assertTrue(
        f.stomach.queued() == 0 && SubLevelSickness.remaining(f.body) == SubLevelSickness.DURATION,
        "Actual rotten-flesh consumption did not start the sickness timer");
    trust(h, f, 12, "Internal transfer or digestion applied another player penalty");
    var saved = f.body.getUserDataTag().copy();
    f.body.setUserDataTag(saved.copy());
    h.assertTrue(
        SubLevelSickness.remaining(f.body) == SubLevelSickness.DURATION,
        "Sickness did not survive a saved-data reload");
    long now = h.getLevel().getGameTime();
    f.stomach.setItem(StomachBlockEntity.PASTE_SLOT, new ItemStack(StomachBlockEntity.paste(), 10));
    SubLevelData.digestiveSickness(
        f.body, new SubLevelData.DigestiveSickness(now + SubLevelSickness.DURATION, now));
    h.assertTrue(
        SubLevelSickness.advance(f.body) == 1
            && f.stomach.getItem(StomachBlockEntity.PASTE_SLOT).getCount() == 9,
        "Vomiting did not remove exactly one paste");
    h.assertTrue(
        SubLevelSickness.advance(f.body) == 0
            && f.stomach.getItem(StomachBlockEntity.PASTE_SLOT).getCount() == 9,
        "Repeated update spammed vomiting within one interval");
    long next = SubLevelData.digestiveSickness(f.body).nextEpisode();
    SubLevelSickness.infect(f.body);
    h.assertTrue(
        SubLevelData.digestiveSickness(f.body).nextEpisode() == next,
        "Refreshing sickness reset the episode throttle");
    SubLevelData.digestiveSickness(f.body, new SubLevelData.DigestiveSickness(now, now));
    h.assertTrue(
        SubLevelSickness.advance(f.body) == 0 && SubLevelSickness.remaining(f.body) == 0,
        "Expired sickness kept vomiting");
    h.assertTrue(
        BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.parse("biomancy:block.digester.crafting_random"))
            != null,
        "Chosen retching sound is not registered");
    SubLevelData.digestiveSickness(f.body, new SubLevelData.DigestiveSickness(now + 600, now));
    SubLevelHealth.damage(f.body, 999, "test");
    h.assertTrue(
        SubLevelSickness.advance(f.body) == 0
            && SubLevelSickness.remaining(f.body) == 0
            && f.stomach.getItem(StomachBlockEntity.PASTE_SLOT).getCount() == 9,
        "Death did not immediately stop sickness effects and paste loss");
    h.succeed();
  }
}
