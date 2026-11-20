package org.azkerash.createbioships.development.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class EstateLandscapeTests {
  @GameTest(template = "test_empty", batch = "estate_landscape", timeoutTicks = 20)
  public static void landscape_preserves_vault_and_roads_and_has_substantial_relief(
      GameTestHelper h) {
    int peak = 0;
    for (int x = -200; x <= 200; x++) {
      for (int z = -200; z <= 200; z++) {
        int y = EstateLandscape.height(x, z);
        peak = Math.max(peak, y);
        h.assertTrue(y >= 0 && y <= 32, "Terrain escaped intended vertical bounds");
        if (EstateLandscape.central(x, z) || EstateLandscape.protectedRoad(x, z)) {
          h.assertTrue(y == 0, "Landscape buries architecture or road");
        }
        if (!EstateLandscape.within(x, z)) {
          h.assertTrue(y == 0, "Terrain fails to taper at outer boundary");
        }
      }
    }
    h.assertTrue(peak >= 20, "Landscape is still effectively flat");
    h.succeed();
  }

  @GameTest(template = "test_empty", batch = "estate_walk_loop", timeoutTicks = 80)
  public static void graded_estate_loop_is_walkable_without_jumping(GameTestHelper h) {
    var l = h.getLevel();
    var base = new net.minecraft.core.BlockPos(9800, 85, 256);
    EstatePaths.build(l, base);
    var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "EstateWalker");
    var player =
        new net.minecraft.server.level.ServerPlayer(
            l.getServer(),
            l,
            profile,
            net.minecraft.server.level.ClientInformation.createDefault());
    player.connection = new net.neoforged.neoforge.common.util.FakePlayer(l, profile).connection;
    var steps = EstatePaths.route();
    player.setPos(steps.getFirst().feet(base));
    player.setOnGround(true);
    for (var step : steps) {
      var target = step.feet(base);
      for (int i = 0; i < 35; i++) {
        var d = target.subtract(player.position());
        if (Math.abs(d.x) + Math.abs(d.z) < .01 && Math.abs(d.y) < .02) {
          break;
        }
        var planar = new net.minecraft.world.phys.Vec3(d.x, 0, d.z);
        if (planar.length() > .15) {
          planar = planar.normalize().scale(.15);
        }
        player.move(
            net.minecraft.world.entity.MoverType.SELF,
            new net.minecraft.world.phys.Vec3(planar.x, -.08, planar.z));
      }
      h.assertTrue(
          player.position().distanceTo(target) < .04,
          "Estate path requires jumping near " + step + "; actual " + player.position());
    }
    h.succeed();
  }
}
