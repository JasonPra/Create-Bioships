package org.Azkerash.create_bioships.components;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.components.logic.brain.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.core.brain.*;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class CardWorkspaceTests {
  private static ServerPlayer player(ServerLevel l, String name) {
    var profile = new GameProfile(UUID.randomUUID(), name);
    var p = new ServerPlayer(l.getServer(), l, profile, ClientInformation.createDefault());
    p.connection = new net.neoforged.neoforge.common.util.FakePlayer(l, profile).connection;
    return p;
  }

  @GameTest(template = "test_empty", batch = "card_editor", timeoutTicks = 40)
  public static void compiled_cards_preserve_identity_and_drafts_are_independent(GameTestHelper h) {
    var p = player(h.getLevel(), "CompilerAuthor");
    var blank = new ItemStack(ComponentRegistry.BLANK_BEHAVIOR_CARD.get());
    blank.set(
        net.minecraft.core.component.DataComponents.CUSTOM_NAME,
        net.minecraft.network.chat.Component.literal("My card"));
    var identity = ProgramCardData.identify(blank);
    p.getInventory().setItem(0, blank);
    p.containerMenu = new CardWorkspaceMenu(30, new CardWorkspaceTarget.Held(p, 0, identity));
    var rejected = CardWorkspaces.compile(p, 29, identity, "Console.WriteLine(1);");
    h.assertTrue(
        !rejected.success() && p.getInventory().getItem(0) == blank,
        "Forged compile changed the card");
    var written = CardWorkspaces.compile(p, 30, identity, "Console.WriteLine(42);");
    var card = p.getInventory().getItem(0);
    h.assertTrue(
        written.success() && ProgramCardData.executable(card),
        "Successful compile did not write an executable card");
    h.assertTrue(
        identity.equals(ProgramCardData.identity(card))
            && card.getHoverName().getString().equals("My card"),
        "Compilation lost identity or custom components");
    h.assertTrue(p.containerMenu.stillValid(p), "Conversion invalidated its own editing lease");
    var loaded =
        ItemStack.parseOptional(
            h.getLevel().registryAccess(),
            (net.minecraft.nbt.CompoundTag) card.save(h.getLevel().registryAccess()));
    h.assertTrue(ProgramCardData.executable(loaded), "Saved program card did not reload");
    var fresh = new ItemStack(ComponentRegistry.BLANK_BEHAVIOR_CARD.get());
    h.assertTrue(
        ProgramCardData.source(fresh).equals(ProgramCardData.TEMPLATE)
            && ProgramCardData.identity(fresh) == null,
        "Fresh card inherited another card's source");
    var copy = card.copy();
    ProgramCardData.saveDraft(copy, "Console.WriteLine(99);");
    h.assertTrue(
        ProgramCardData.source(card).equals("Console.WriteLine(42);")
            && !ProgramCardData.executable(copy),
        "Copy shared mutable source or changed source stayed executable");
    p.containerMenu = new CardWorkspaceMenu(31, new CardWorkspaceTarget.Held(p, 0, identity));
    var invalid = CardWorkspaces.compile(p, 31, identity, "int value = true;");
    h.assertTrue(
        !invalid.success()
            && invalid.message().contains("Line 1")
            && ProgramCardData.executable(card),
        "Failed compilation corrupted the working card or lost diagnostic");
    h.assertTrue(
        CardWorkspaces.save(p, 31, identity, "Console.WriteLine(43);")
            && !ProgramCardData.executable(card),
        "Saving changed source left old executable enabled");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "card_editor", timeoutTicks = 40)
  public static void drafts_round_trip_on_the_physical_card_and_reject_stale_edits(
      GameTestHelper h) {
    var l = h.getLevel();
    var p = player(l, "CardAuthor");
    var card = new ItemStack(ComponentRegistry.BLANK_BEHAVIOR_CARD.get());
    p.getInventory().setItem(0, card);
    var identity = ProgramCardData.identify(card);
    var target = new CardWorkspaceTarget.Held(p, 0, identity);
    p.containerMenu = new CardWorkspaceMenu(17, target);
    String source = "int count = 7;\nConsole.WriteLine(count);";
    h.assertTrue(
        CardWorkspaces.save(p, 17, identity, source), "Valid physical-card draft was rejected");
    var loaded =
        ItemStack.parseOptional(
            l.registryAccess(), (net.minecraft.nbt.CompoundTag) card.save(l.registryAccess()));
    h.assertTrue(
        ProgramCardData.source(loaded).equals(source)
            && identity.equals(ProgramCardData.identity(loaded)),
        "Card save/load lost source or identity");
    var wire =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), l.registryAccess());
    ItemStack.OPTIONAL_STREAM_CODEC.encode(wire, card);
    var publicCard = ItemStack.OPTIONAL_STREAM_CODEC.decode(wire);
    wire.release();
    h.assertTrue(
        !ProgramCardData.document(publicCard).contains("Source")
            && !ProgramCardData.document(publicCard).getBoolean("Compiled"),
        "Public item synchronization disclosed source or executable state");
    h.assertTrue(
        !org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.restore(
            player(l, "OtherAuthor"), publicCard),
        "Another player recovered private source from a public card marker");
    h.assertTrue(
        org.Azkerash.create_bioships.core.brain.program.PrivateCardBackups.restore(p, publicCard)
            && ProgramCardData.source(publicCard).equals(source),
        "Creative item-copy restoration lost the author's source");
    h.assertTrue(
        !ProgramCardData.document(loaded).getBoolean("Compiled"),
        "Saving a draft made it executable");
    h.assertTrue(!CardWorkspaces.save(p, 99, identity, "wrong menu"), "Forged menu accepted");
    h.assertTrue(
        !CardWorkspaces.save(p, 17, UUID.randomUUID(), "wrong identity"),
        "Forged card identity accepted");
    h.assertTrue(
        !CardWorkspaces.save(p, 17, identity, "x".repeat(ProgramCardData.MAX_SOURCE + 1)),
        "Oversized source accepted");
    p.getInventory().setItem(0, new ItemStack(ComponentRegistry.BLANK_BEHAVIOR_CARD.get()));
    h.assertTrue(
        !CardWorkspaces.save(p, 17, identity, "stale"),
        "Replacing card did not invalidate editor lease");
    h.assertTrue(
        ProgramCardData.source(card).equals(source), "Rejected edit altered the original card");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "card_editor_permissions", timeoutTicks = 60)
  public static void brain_editor_requires_owner_or_invite_and_revocation_closes_access(
      GameTestHelper h) {
    var l = h.getLevel();
    var origin = h.absolutePos(new BlockPos(1, 2, 1));
    l.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    l.setBlock(origin.east(), ComponentRegistry.BRAIN.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, origin, List.of(origin, origin.east()), new BoundingBox3i(origin, origin.east()));
    CoreAssemblyRules.mark(l, body.getPlot().getCenterBlock());
    org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport.pause(body);
    var owner = player(l, "BrainOwner");
    var guest = player(l, "BrainGuest");
    SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
    var brain = SubLevelBrains.get(body);
    var port = brain.port();
    var point = body.logicalPose().transformPosition(Vec3.atCenterOf(port));
    owner.setPos(point);
    guest.setPos(point);
    var card = new ItemStack(ComponentRegistry.BLANK_BEHAVIOR_CARD.get());
    brain.inventory().setItem(0, card);
    var identity = ProgramCardData.identify(card);
    brain.save();
    var target = new CardWorkspaceTarget.Brain(brain.inventory(), port, 0, identity);
    h.assertTrue(
        target.valid(owner) && !target.valid(guest),
        "Brain source exposed to outsider or owner rejected");
    h.assertTrue(
        SubLevelPermissions.add(body, owner.getUUID(), guest) && target.valid(guest),
        "Invited player could not edit");
    guest.containerMenu = new CardWorkspaceMenu(18, target);
    h.assertTrue(
        CardWorkspaces.save(guest, 18, identity, "int invited = 1;"), "Invited edit was rejected");
    SubLevelPermissions.remove(body, owner.getUUID(), guest.getUUID());
    h.assertTrue(
        !guest.containerMenu.stillValid(guest)
            && !CardWorkspaces.save(guest, 18, identity, "stolen"),
        "Revoked access retained editor rights");
    owner.containerMenu = new BrainMenu(19, owner.getInventory(), brain.inventory(), port);
    guest.containerMenu = new BrainMenu(20, guest.getInventory(), brain.inventory(), port);
    h.assertTrue(
        owner.containerMenu.stillValid(owner) && !guest.containerMenu.stillValid(guest),
        "Existing brain inventory bypassed permissions");
    h.succeed();
  }
}
