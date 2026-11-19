package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions;
import org.Azkerash.create_bioships.development.testing.DevelopmentLifeSupport;
import org.Azkerash.create_bioships.equipment.DiagnosticAccess;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class PlayerDiagnosticTests {
  @GameTest(template = "test_empty", batch = "player_diagnostics", timeoutTicks = 80)
  public static void
      normal_player_tool_discloses_only_owned_or_invited_creatures_and_requires_the_item(
          GameTestHelper h) {
    var l = h.getLevel();
    var p = h.absolutePos(new BlockPos(1, 3, 1));
    l.setBlock(p, ComponentRegistry.CORE.get().defaultBlockState(), 3);
    var b = SubLevelAssemblyHelper.assembleBlocks(l, p, List.of(p), new BoundingBox3i(p, p));
    CoreAssemblyRules.mark(l, b.getPlot().getCenterBlock());
    DevelopmentLifeSupport.pause(b);
    var owner =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DiagnosticOwner"));
    var guest =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DiagnosticGuest"));
    var stranger =
        new net.neoforged.neoforge.common.util.FakePlayer(
            l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DiagnosticStranger"));
    SubLevelPermissions.assignIfUnowned(b, owner.getUUID());
    h.assertTrue(!DiagnosticAccess.canInspect(b, owner), "Missing instrument allowed disclosure");
    for (var player : List.of(owner, guest, stranger))
      player
          .getInventory()
          .setItem(0, Create_bioships.SOMATIC_RESONATOR.get().getDefaultInstance());
    h.assertTrue(
        !org.Azkerash.create_bioships.development.testing.DevelopmentToolkitItem.allowed(owner),
        "Player instrument granted development-only privileges");
    h.assertTrue(
        DiagnosticAccess.allowed(owner)
            && DiagnosticAccess.canInspect(b, owner)
            && !DiagnosticAccess.canInspect(b, stranger)
            && !DiagnosticAccess.canInspect(b, guest),
        "Instrument bypassed ownership or required development mode");
    h.assertTrue(
        SubLevelPermissions.add(b, owner.getUUID(), guest) && DiagnosticAccess.canInspect(b, guest),
        "Invited player could not inspect");
    SubLevelPermissions.remove(b, owner.getUUID(), guest.getUUID());
    h.assertTrue(!DiagnosticAccess.canInspect(b, guest), "Revocation retained diagnostic access");
    owner.getInventory().setItem(0, ItemStack.EMPTY);
    h.assertTrue(
        !DiagnosticAccess.allowed(owner) && !DiagnosticAccess.canInspect(b, owner),
        "Removing tool retained data access");
    h.assertTrue(
        l.getRecipeManager()
            .byKey(
                net.minecraft.resources.ResourceLocation.parse("create_bioships:somatic_resonator"))
            .isPresent(),
        "Player tool has no crafting recipe");
    h.succeed();
  }
}
