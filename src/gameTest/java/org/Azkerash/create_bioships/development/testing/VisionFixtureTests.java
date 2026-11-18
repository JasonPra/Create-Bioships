package org.Azkerash.create_bioships.development.testing;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.vision.*;
import org.Azkerash.create_bioships.development.testing.network.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class VisionFixtureTests {
  @GameTest(template = "test_empty", batch = "vision_fixture", timeoutTicks = 200)
  public static void visible_vision_start_reset_water_and_wire_snapshot(GameTestHelper helper) {
    var level = helper.getLevel();
    VisionFixture.reset(level);
    helper.assertTrue(
        VisionFixture.start(level) == 1 && VisionFixture.start(level) == 0,
        "Visible fixture START was not repeatable/idempotent");
    helper.runAfterDelay(
        50,
        () -> {
          var bodies = new ArrayList<ServerSubLevel>();
          for (var entry : SubLevelContainer.getContainer(level).getAllSubLevels())
            if (entry instanceof ServerSubLevel body
                && body.getUserDataTag() != null
                && body.getUserDataTag().getBoolean("create_bioships:vision_fixture"))
              bodies.add(body);
          helper.assertTrue(bodies.size() == 2, "Fixture failed to assemble both subjects");
          var all = new ArrayList<SubLevelVision.Sight>();
          for (var body : bodies) {
            SubLevelVision.refresh(body, 100);
            all.addAll(SubLevelVision.sights(body));
          }
          helper.assertTrue(
              all.size() == 16
                  && all.stream().anyMatch(SubLevelVision.Sight::underwater)
                  && all.stream().anyMatch(s -> !s.underwater()),
              "Air/water fixture sensors did not sample both environments");
          var payload =
              new VisionDebugPayload(bodies.getFirst().getUniqueId(), all.getFirst().rays());
          var buffer =
              new RegistryFriendlyByteBuf(
                  io.netty.buffer.Unpooled.buffer(), level.registryAccess());
          VisionDebugPayload.CODEC.encode(buffer, payload);
          helper.assertTrue(
              VisionDebugPayload.CODEC.decode(buffer).equals(payload),
              "Vision wire data lost ray endpoints");
          buffer.release();
          for (var body : bodies) SubLevelHealth.damage(body, 10000, "vision_test");
          helper.assertTrue(
              bodies.stream().allMatch(b -> SubLevelVision.sights(b).isEmpty()),
              "Dead fixture still exposes vision");
          VisionFixture.reset(level);
          helper.assertTrue(
              bodies.stream().allMatch(ServerSubLevel::isRemoved), "RESET retained old subject");
          helper.assertTrue(VisionFixture.start(level) == 1, "RESET did not allow a second START");
          VisionFixture.reset(level);
          helper.succeed();
        });
  }
}
