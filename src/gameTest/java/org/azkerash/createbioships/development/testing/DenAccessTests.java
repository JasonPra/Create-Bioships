package org.azkerash.createbioships.development.testing;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class DenAccessTests {
  @GameTest(template = "test_empty", batch = "den_stair_access", timeoutTicks = 60)
  public static void tower_and_vault_stairs_walk_both_directions_without_jumping(GameTestHelper h) {
    var l = h.getLevel();
    for (boolean vault : new boolean[] {false, true}) {
      var base = new BlockPos(vault ? 9300 : 9200, 85, 256);
      for (int x = -7; x <= 7; x++) {
        for (int z = -7; z <= 7; z++) {
          l.setChunkForced((base.getX() + x) >> 4, (base.getZ() + z) >> 4, true);
        }
      }
      int height = vault ? 56 : 48;
      if (vault) {
        var center = base.offset(0, 0, 30);
        NecromancerDenFixture.supportFrameWithAccess(l, center, center.above(height));
      } else {
        DenStaircase.build(l, base, height, false);
      }
      var profile = new GameProfile(UUID.randomUUID(), "StairWalker");
      var player = new ServerPlayer(l.getServer(), l, profile, ClientInformation.createDefault());
      player.connection = new net.neoforged.neoforge.common.util.FakePlayer(l, profile).connection;
      var route = DenStaircase.route(height);
      player.setPos(route.getFirst().feet(base));
      player.setOnGround(true);
      for (var step : route) {
        walk(h, player, step.feet(base));
      }
      for (var step : route.reversed()) {
        walk(h, player, step.feet(base));
      }
      for (int y : vault ? new int[] {0, height} : new int[] {0, 12, 24, 36, 48}) {
        h.assertTrue(
            l.getBlockState(base.offset(3, y, 5)).isAir()
                && l.getBlockState(base.offset(3, y + 1, 5)).isAir(),
            "Blocked floor doorway");
      }
    }
    h.succeed();
  }

  private static void walk(GameTestHelper h, ServerPlayer player, Vec3 target) {
    for (int i = 0; i < 30; i++) {
      var d = target.subtract(player.position());
      if (Math.abs(d.x) + Math.abs(d.z) < .005 && Math.abs(d.y) < .02) {
        return;
      }
      var planar = new Vec3(d.x, 0, d.z);
      if (planar.length() > .15) {
        planar = planar.normalize().scale(.15);
      }
      player.move(MoverType.SELF, new Vec3(planar.x, -.08, planar.z));
    }
    h.assertTrue(
        player.position().distanceTo(target) < .03,
        "Stair is not walkable: from " + player.position() + " to " + target);
  }
}
