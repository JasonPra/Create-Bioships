package org.azkerash.createbioships.core;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.compat.letitwalk.*;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.components.logic.diagnostics.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class DebugPanelTests {
  @GameTest(template = "test_empty", batch = "debug_panel")
  public static void diagnostics_report_real_settings_and_protect_remote_requests(
      GameTestHelper h) {
    var level = h.getLevel();
    var at = h.absolutePos(new BlockPos(2, 4, 2));
    level.setBlock(at, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(at.above(), ComponentRegistry.BRAIN.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.east(), ComponentRegistry.ARACHNID_LEG.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(
        at.west(), ComponentRegistry.DEBUG_PANEL.get().defaultBlockState(), Block.UPDATE_ALL);
    var body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            at,
            List.of(at, at.above(), at.east(), at.west()),
            new BoundingBox3i(at.west(), at.east().above()));
    var core = body.getPlot().getCenterBlock();
    var panel = core.west();
    CoreAssemblyRules.mark(level, core);
    DevelopmentLifeSupport.pause(body);
    new SocketJointSettings(0, 0).save(body, SocketJointSettings.compact(core.east(), 1));
    var rows = CreatureDiagnostics.inspect(body);
    h.assertTrue(
        rows.stream()
            .anyMatch(
                row ->
                    row.section().equals("Legs")
                        && row.severity() == 2
                        && row.detail().contains("J2 swivel=0 bend=+/-0")
                        && row.suggestion().contains("zero swivel")),
        "Locked joint was not identified with a specific suggestion");
    h.assertTrue(
        rows.stream()
            .anyMatch(
                row ->
                    row.section().equals("Components")
                        && row.title().equals("create_bioships:debug_panel")),
        "Installed components were not reported");
    h.assertTrue(
        rows.stream().noneMatch(row -> row.title().startsWith("Suspected:")),
        "Idle creature was falsely declared stuck");
    var original =
        new DebugPanelPayload(body.getUniqueId(), panel, level.getGameTime(), true, rows);
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), level.registryAccess());
    DebugPanelPayload.CODEC.encode(buffer, original);
    h.assertTrue(
        original.equals(DebugPanelPayload.CODEC.decode(buffer)),
        "Diagnostic snapshot lost data in transit");
    buffer.release();
    var owner =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new GameProfile(UUID.randomUUID(), "DebugOwner"));
    var visitor =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new GameProfile(UUID.randomUUID(), "DebugVisitor"));
    var near = body.logicalPose().transformPosition(Vec3.atCenterOf(panel));
    owner.setPos(near);
    visitor.setPos(near);
    SubLevelPermissions.assignIfUnowned(body, owner.getUUID());
    h.assertTrue(
        DebugPanelControl.usable(owner, body, panel), "Nearby owner could not inspect its panel");
    h.assertTrue(
        !DebugPanelControl.usable(visitor, body, panel),
        "Visitor could inspect private creature diagnostics");
    h.assertTrue(
        !DebugPanelControl.refresh(owner, new DebugPanelPayload.Request(UUID.randomUUID(), panel)),
        "Forged grid request was accepted");
    owner.setPos(near.add(100, 0, 0));
    h.assertTrue(
        !DebugPanelControl.usable(owner, body, panel),
        "Panel allowed remote inspection outside reach");
    h.succeed();
  }
}
