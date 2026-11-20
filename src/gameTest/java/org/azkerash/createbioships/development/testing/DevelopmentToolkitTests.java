package org.azkerash.createbioships.development.testing;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;
import org.azkerash.createbioships.development.testing.network.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class DevelopmentToolkitTests {
  @GameTest(template = "test_empty", timeoutTicks = 100, batch = "development_tool_protocol")
  public static void path_snapshot_roundtrip_bounds_and_normal_world_restrictions(
      GameTestHelper h) {
    var level = h.getLevel();
    for (var mode : SubLevelPathfinder.Mode.values()) {
      var path =
          new PathDebugPayload(
              UUID.randomUUID(),
              mode,
              new Vec3(4, 8, 3),
              List.of(new Vec3(4, 7, 3), new Vec3(5, 8, 4)),
              1);
      var b =
          new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
      PathDebugPayload.CODEC.encode(b, path);
      h.assertTrue(
          PathDebugPayload.CODEC.decode(b).equals(path),
          "Path codec lost world points, mode or waypoint");
      b.release();
    }
    boolean rejected = false;
    try {
      new PathDebugPayload(
          UUID.randomUUID(), SubLevelPathfinder.Mode.GROUND, Vec3.ZERO, List.of(Vec3.ZERO), 1);
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    h.assertTrue(rejected, "Invalid waypoint accepted");
    var b = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
    b.writeUUID(UUID.randomUUID());
    b.writeEnum(SubLevelPathfinder.Mode.GROUND);
    b.writeDouble(0);
    b.writeDouble(0);
    b.writeDouble(0);
    b.writeVarInt(257);
    rejected = false;
    try {
      PathDebugPayload.CODEC.decode(b);
    } catch (IllegalArgumentException e) {
      rejected = true;
    } finally {
      b.release();
    }
    h.assertTrue(rejected, "Oversized route accepted");
    var player =
        new net.neoforged.neoforge.common.util.FakePlayer(
            level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ToolkitOutside"));
    h.assertTrue(
        !DevelopmentLoadout.active(player), "Normal test world became a development world");
    player
        .getInventory()
        .setItem(
            DevelopmentLoadout.TOOLKIT_SLOT,
            DevelopmentToolkitItem.ITEM.get().getDefaultInstance());
    h.assertTrue(
        !DevelopmentToolkitItem.allowed(player), "Normal-world toolkit authorized debug access");
    DevelopmentLoadout.ensureNavigator(player);
    h.assertTrue(
        !player.getInventory().contains(DevelopmentToolkitItem.ITEM.get().getDefaultInstance()),
        "Toolkit remained obtainable in normal world");
    h.succeed();
  }
}
