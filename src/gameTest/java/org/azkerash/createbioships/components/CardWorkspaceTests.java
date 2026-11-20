package org.azkerash.createbioships.components;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.logic.brain.*;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.core.brain.program.ProgramCardData;

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
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    buffer.writeUUID(identity);
    buffer.writeUtf(ProgramCardData.source(card), ProgramCardData.MAX_SOURCE);
    buffer.writeBoolean(true);
    var clientMenu = CardWorkspaceMenu.fromNetwork(32, p.getInventory(), buffer);
    h.assertTrue(
        clientMenu.isInitiallyCompiled()
            && clientMenu.getIdentity().equals(identity)
            && clientMenu.getInitialSource().equals(ProgramCardData.source(card)),
        "Private editor opening lost source or compiled status");
    buffer.clear();
    var packet =
        new CardWorkspaceNetwork.Output(32, identity, "COMPLETED", List.of("private line", "42"));
    CardWorkspaceNetwork.Output.CODEC.encode(buffer, packet);
    h.assertTrue(
        CardWorkspaceNetwork.Output.CODEC.decode(buffer).equals(packet),
        "Private console payload did not round trip");
    buffer.release();
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
        !org.azkerash.createbioships.core.brain.program.PrivateCardBackups.restore(
            player(l, "OtherAuthor"), publicCard),
        "Another player recovered private source from a public card marker");
    h.assertTrue(
        org.azkerash.createbioships.core.brain.program.PrivateCardBackups.restore(p, publicCard)
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

  @GameTest(template = "test_empty", batch = "program_runtime", timeoutTicks = 60)
  public static void program_cards_run_once_and_output_requires_a_live_authorized_lease(
      GameTestHelper h) {
    var l = h.getLevel();
    var origin = h.absolutePos(new BlockPos(1, 2, 1));
    l.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(origin.east(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(origin.east(2), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l,
            origin,
            List.of(origin, origin.east(), origin.east(2)),
            new BoundingBox3i(origin, origin.east(2)));
    CoreAssemblyRules.mark(l, body.getPlot().getCenterBlock());
    org.azkerash.createbioships.development.testing.DevelopmentLifeSupport.pause(body);
    var owner = player(l, "ProgramOwner");
    var guest = player(l, "ProgramGuest");
    SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
    var brain = SubLevelBrains.get(body);
    var memory = brain.inventory();
    var port = brain.port();
    var point = body.logicalPose().transformPosition(Vec3.atCenterOf(port));
    owner.setPos(point);
    guest.setPos(point);
    var blank = new ItemStack(ComponentRegistry.BLANK_BEHAVIOR_CARD.get());
    var card =
        ProgramCardData.compiledCopy(
            blank, "Console.WriteLine(\"private output\"); Console.WriteLine(Brain.Health);");
    memory.setItem(0, card);
    var identity = ProgramCardData.identity(card);
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    var target = new CardWorkspaceTarget.Brain(memory, port, 0, identity);
    owner.containerMenu = new CardWorkspaceMenu(40, target);
    guest.containerMenu = new CardWorkspaceMenu(41, target);
    var output = CardWorkspaces.output(owner, 40, identity);
    h.assertTrue(
        output != null
            && output.state().equals("COMPLETED")
            && output.output().size() == 2
            && output.output().getFirst().equals("private output"),
        "Program did not execute once or lost private output");
    h.assertTrue(
        CardWorkspaces.output(guest, 41, identity).output().isEmpty(),
        "Outsider read runtime output");
    SubLevelPermissions.add(body, owner.getUUID(), guest);
    guest.containerMenu = new CardWorkspaceMenu(42, target);
    h.assertTrue(
        CardWorkspaces.output(guest, 42, identity).output().size() == 2,
        "Invited viewer could not read output");
    SubLevelPermissions.remove(body, owner.getUUID(), guest.getUUID());
    h.assertTrue(
        CardWorkspaces.output(guest, 42, identity).output().isEmpty(),
        "Revoked viewer retained output access");
    ProgramCardData.saveDraft(card, "Console.WriteLine(\"changed\");");
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    h.assertTrue(
        org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.active(body) == null,
        "Changed draft continued executing old code");
    card = ProgramCardData.compiledCopy(card, "Console.WriteLine(\"replacement\");");
    memory.setItem(0, card);
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    h.assertTrue(
        org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.programView(body, card)
            .output()
            .equals(List.of("replacement")),
        "Replacement card did not restart with new source");
    memory.setItem(1, card);
    memory.setItem(0, new ItemStack(ComponentRegistry.WANDER_BEHAVIOR_CARD.get()));
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    h.assertTrue(
        org.azkerash.createbioships.core.brain.behavior.WanderBehavior.ID.equals(
            org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.active(body)),
        "Program bypassed inventory-order factory selection");
    memory.setItem(0, ItemStack.EMPTY);
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    h.assertTrue(
        org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.PROGRAM_ID.equals(
            org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.active(body)),
        "Program in next active slot did not start");
    memory.setItem(1, ItemStack.EMPTY);
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    h.assertTrue(
        org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.active(body) == null,
        "Removed card kept running");
    memory.setItem(0, card);
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    SubLevelHealth.damage(body, SubLevelHealth.status(body).current() + 1, "program_test");
    org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.advance(body);
    h.assertTrue(
        org.azkerash.createbioships.core.brain.behavior.BrainBehaviors.active(body) == null,
        "Dead creature kept running a program");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "card_editor_permissions", timeoutTicks = 60)
  public static void brain_editor_requires_owner_or_invite_and_revocation_closes_access(
      GameTestHelper h) {
    var l = h.getLevel();
    var origin = h.absolutePos(new BlockPos(1, 2, 1));
    l.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    l.setBlock(origin.east(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, origin, List.of(origin, origin.east()), new BoundingBox3i(origin, origin.east()));
    CoreAssemblyRules.mark(l, body.getPlot().getCenterBlock());
    org.azkerash.createbioships.development.testing.DevelopmentLifeSupport.pause(body);
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
