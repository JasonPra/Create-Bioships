package org.Azkerash.create_bioships.core;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class WrenchOwnershipTests {
  @GameTest(template = "test_empty", batch = "wrench_ownership", timeoutTicks = 100)
  public static void unassigned_stranger_and_invited_removal_fail_but_current_owner_succeeds(
      GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(1, 3, 1));
    l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    l.setBlock(p.east(), ComponentRegistry.CHITIN_ARMOR.get().defaultBlockState(), 3);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            l, p, List.of(p, p.east()), new BoundingBox3i(p, p.east()));
    var core = body.getPlot().getCenterBlock();
    var cell = core.east();
    CoreAssemblyRules.mark(l, core);
    DevelopmentLifeSupport.pause(body);
    var owner = new FakePlayer(l, new GameProfile(UUID.randomUUID(), "WrenchOwner"));
    var guest = new FakePlayer(l, new GameProfile(UUID.randomUUID(), "WrenchGuest"));
    var stranger = new FakePlayer(l, new GameProfile(UUID.randomUUID(), "WrenchStranger"));
    var wrench = Create_bioships.BIOSHIP_WRENCH.get();
    for (var actor : List.of(owner, guest, stranger)) {
      actor.setShiftKeyDown(true);
      actor.setItemInHand(InteractionHand.MAIN_HAND, wrench.getDefaultInstance());
      var hit = new BlockHitResult(Vec3.atCenterOf(cell), Direction.NORTH, cell, false);
      h.assertTrue(
          wrench.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit))
                  == InteractionResult.FAIL
              && l.getBlockState(cell).is(ComponentRegistry.CHITIN_ARMOR.get()),
          "Unassigned creature allowed component removal");
    }
    h.assertTrue(
        SubLevelPermissions.get(body).owner() == null, "Attempted pickup claimed the creature");
    SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
    SubLevelPermissions.add(body, owner.getUUID(), guest);
    for (var actor : List.of(guest, stranger)) {
      var hit = new BlockHitResult(Vec3.atCenterOf(cell), Direction.NORTH, cell, false);
      h.assertTrue(
          wrench.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit))
                  == InteractionResult.FAIL
              && l.getBlockState(cell).is(ComponentRegistry.CHITIN_ARMOR.get()),
          "Inspection permission granted removal authority");
    }
    h.assertTrue(
        SubLevelPermissions.transfer(body, owner.getUUID(), guest.getUUID()),
        "Ownership transfer failed");
    var hit = new BlockHitResult(Vec3.atCenterOf(cell), Direction.NORTH, cell, false);
    h.assertTrue(
        wrench.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit))
            == InteractionResult.FAIL,
        "Previous owner retained removal authority");
    double damage = SubLevelHealth.status(body).damage();
    var result = wrench.useOn(new UseOnContext(guest, InteractionHand.MAIN_HAND, hit));
    h.assertTrue(
        result.consumesAction()
            && l.getBlockState(cell).isAir()
            && !SubLevelHealth.status(body).dead()
            && SubLevelHealth.status(body).damage() == damage,
        "Current owner could not remove a component without adding injury");
    h.succeed();
  }
}
