package org.azkerash.createbioships.core;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.core.access.*;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class PlacementInterceptionTests {
  private static ServerPlayer player(ServerLevel level, String name) {
    var profile = new GameProfile(UUID.randomUUID(), name);
    var player =
        new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
    player.connection = new FakePlayer(level, profile).connection;
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  private static int fixtureIndex;

  private static ServerSubLevel body(GameTestHelper h) {
    var level = h.getLevel();
    var origin = new BlockPos(9600 + fixtureIndex++ * 64, 100, 128);
    level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
    level.setBlock(origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level, origin, List.of(origin), new BoundingBox3i(origin, origin));
    DevelopmentLifeSupport.pause(body);
    return body;
  }

  private static BlockHitResult hit(BlockPos cell, Direction face) {
    return new BlockHitResult(Vec3.atCenterOf(cell).relative(face, .5), face, cell, false);
  }

  @GameTest(template = "test_empty", batch = "placement_interception", timeoutTicks = 60)
  public static void all_block_items_are_gated_and_owner_and_invite_can_build(GameTestHelper h) {
    var level = h.getLevel();
    var body = body(h);
    var core = body.getPlot().getCenterBlock();
    var owner = player(level, "BuilderOwner");
    var guest = player(level, "BuilderGuest");
    var stranger = player(level, "BuilderStranger");
    h.assertTrue(
        !SubLevelInteractionInterceptor.canModify(owner.getUUID(), body),
        "Unassigned construct accepted building");
    SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
    SubLevelPermissions.add(body, owner.getUUID(), guest);
    int checked = 0;
    for (var item : BuiltInRegistries.ITEM) {
      if (!(item instanceof BlockItem)) {
        continue;
      }
      stranger.setItemInHand(InteractionHand.MAIN_HAND, item.getDefaultInstance());
      var click =
          new PlayerInteractEvent.RightClickBlock(
              stranger, InteractionHand.MAIN_HAND, core, hit(core, Direction.UP));
      SubLevelInteractionInterceptor.rightClick(click);
      h.assertTrue(click.isCanceled(), "Block item bypassed generic gate: " + item);
      var itemUse =
          new net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent(
              new net.minecraft.world.item.context.UseOnContext(
                  stranger, InteractionHand.MAIN_HAND, hit(core, Direction.UP)),
              net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase
                  .ITEM_AFTER_BLOCK);
      SubLevelInteractionInterceptor.useItem(itemUse);
      h.assertTrue(itemUse.isCanceled(), "Direct item-use phase bypassed generic gate: " + item);
      checked++;
    }
    h.assertTrue(checked > 100, "Registry-wide placement coverage was empty");
    int before = SubLevelContainer.getContainer(level).getAllSubLevels().size();
    for (var item :
        List.of(ComponentRegistry.CHITIN_ARMOR.get().asItem(), ComponentRegistry.CORE_ITEM.get())) {
      var stack = new ItemStack(item, 3);
      stranger.setItemInHand(InteractionHand.MAIN_HAND, stack);
      stranger.gameMode.useItemOn(
          stranger, level, stack, InteractionHand.MAIN_HAND, hit(core, Direction.UP));
      h.assertTrue(
          level.getBlockState(core.above()).isAir() && stack.getCount() == 3,
          "Unauthorized click placed/consumed a block");
      stack.useOn(
          new net.minecraft.world.item.context.UseOnContext(
              stranger, InteractionHand.MAIN_HAND, hit(core, Direction.UP)));
      h.assertTrue(
          level.getBlockState(core.above()).isAir() && stack.getCount() == 3,
          "Direct ItemStack use bypassed the pre-placement gate");
    }
    h.assertTrue(
        SubLevelContainer.getContainer(level).getAllSubLevels().size() == before,
        "Unauthorized core click created a sublevel");
    for (var actor : List.of(owner, guest)) {
      Direction side = actor == owner ? Direction.EAST : Direction.WEST;
      var stack = new ItemStack(ComponentRegistry.CHITIN_ARMOR.get());
      actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
      actor.gameMode.useItemOn(actor, level, stack, InteractionHand.MAIN_HAND, hit(core, side));
      h.assertTrue(
          level.getBlockState(core.relative(side)).is(ComponentRegistry.CHITIN_ARMOR.get()),
          "Authorized builder could not place armor: "
              + actor.getName().getString()
              + " permission="
              + SubLevelInteractionInterceptor.canModify(actor.getUUID(), body)
              + " item="
              + stack
              + " state="
              + level.getBlockState(core.relative(side)));
    }
    h.assertTrue(
        !SubLevelInteractionInterceptor.canRemoveComponent(guest.getUUID(), body),
        "Building permission silently granted owner-only wrench extraction");
    var mouth = core.north();
    level.setBlock(mouth, ComponentRegistry.MOUTH.get().defaultBlockState(), Block.UPDATE_ALL);
    for (var actor : List.of(guest, stranger)) {
      actor.setShiftKeyDown(true);
      actor.setItemInHand(InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
      actor.gameMode.useItemOn(
          actor,
          level,
          actor.getMainHandItem(),
          InteractionHand.MAIN_HAND,
          hit(mouth, Direction.NORTH));
      h.assertTrue(
          level.getBlockState(mouth).is(ComponentRegistry.MOUTH.get()),
          "Native Create wrench bypassed owner-only extraction");
      actor.setShiftKeyDown(false);
    }
    h.assertTrue(
        SubLevelPermissions.transfer(body, owner.getUUID(), stranger.getUUID()),
        "Ownership transfer failed");
    h.assertTrue(
        !SubLevelInteractionInterceptor.canModify(owner.getUUID(), body)
            && SubLevelInteractionInterceptor.canModify(stranger.getUUID(), body),
        "Gate cached stale ownership");
    System.out.println("PLACEMENT_INTERCEPTOR registeredBlockItems=" + checked);
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "core_placement_interceptor", timeoutTicks = 60)
  public static void authorized_core_is_separated_by_exact_cell_without_blink_validation(
      GameTestHelper h) {
    var level = h.getLevel();
    var parent = body(h);
    var core = parent.getPlot().getCenterBlock();
    var owner = player(level, "CoreBuilder");
    SubLevelPermissions.assignIfUnowned(parent, owner.getUUID());
    var stack = new ItemStack(ComponentRegistry.CORE_ITEM.get(), 2);
    owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
    var expected = parent.logicalPose().transformPosition(Vec3.atCenterOf(core.east()));
    owner.gameMode.useItemOn(
        owner, level, stack, InteractionHand.MAIN_HAND, hit(core, Direction.EAST));
    h.assertTrue(
        level.getBlockState(core.east()).is(ComponentRegistry.CORE.get()),
        "Authorized core placement was rejected");
    // Flush the same post-placement interceptor before a scheduled blink can run.
    CoreAssemblyRules.assemblePlayerPlacements(
        new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, level.getServer()));
    h.assertTrue(
        level.getBlockState(core.east()).isAir()
            && level.getBlockState(core).is(ComponentRegistry.CORE.get()),
        "Interceptor did not extract only the newly placed core");
    var child =
        SubLevelContainer.getContainer(level).getAllSubLevels().stream()
            .filter(entry -> entry instanceof ServerSubLevel && entry != parent)
            .map(entry -> (ServerSubLevel) entry)
            .filter(
                entry ->
                    entry
                            .logicalPose()
                            .transformPosition(Vec3.atCenterOf(entry.getPlot().getCenterBlock()))
                            .distanceTo(expected)
                        < .001)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing child at "
                            + expected
                            + ": "
                            + SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                                .filter(entry -> entry instanceof ServerSubLevel && entry != parent)
                                .map(
                                    entry ->
                                        entry
                                            .logicalPose()
                                            .transformPosition(
                                                Vec3.atCenterOf(entry.getPlot().getCenterBlock()))
                                            .toString())
                                .toList()));
    h.assertTrue(
        CoreAssemblyRules.isCreature(child)
            && SubLevelPermissions.get(child).owner().equals(owner.getUUID())
            && !child.getUniqueId().equals(parent.getUniqueId()),
        "Detached core lost identity, pose or ownership");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "break_interception", timeoutTicks = 60)
  public static void unauthorized_mining_is_blocked_but_attacks_still_damage_the_creature(
      GameTestHelper h) {
    var level = h.getLevel();
    var body = body(h);
    var core = body.getPlot().getCenterBlock();
    var owner = player(level, "MiningOwner");
    var stranger = player(level, "MiningStranger");
    SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
    CoreAssemblyRules.mark(level, core);
    org.azkerash.createbioships.combat.CombatAdapter.target(body);
    level.setBlock(
        core.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), Block.UPDATE_ALL);
    h.assertTrue(
        !stranger.gameMode.destroyBlock(core.east())
            && level.getBlockState(core.east()).is(ComponentRegistry.CHITIN_ARMOR.get()),
        "Native destruction bypassed the shared gate");
    stranger.resetAttackStrengthTicker();
    stranger.tick();
    stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
    double before = SubLevelHealth.status(body).current();
    var click =
        new PlayerInteractEvent.LeftClickBlock(
            stranger, core, Direction.NORTH, PlayerInteractEvent.LeftClickBlock.Action.START);
    NeoForge.EVENT_BUS.post(click);
    h.assertTrue(
        click.isCanceled()
            && SubLevelHealth.status(body).current() < before
            && level.getBlockState(core).is(ComponentRegistry.CORE.get()),
        "Permission gate prevented combat damage or allowed harvesting living tissue");
    SubLevelHealth.damage(body, SubLevelHealth.status(body).current(), "test");
    h.assertTrue(
        !stranger.gameMode.destroyBlock(core.east()), "Corpse mining ignored access permissions");
    h.assertTrue(
        owner.gameMode.destroyBlock(core.east()), "Authorized owner could not mine corpse tissue");
    h.succeed();
  }
}
