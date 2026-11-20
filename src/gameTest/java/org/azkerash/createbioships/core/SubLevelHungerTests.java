package org.azkerash.createbioships.core;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.combat.CombatAdapter;
import org.azkerash.createbioships.components.ComponentRegistry;
import org.azkerash.createbioships.development.testing.DevelopmentLifeSupport;

@GameTestHolder(CreateBioships.MODID)
@PrefixGameTestTemplate(false)
public final class SubLevelHungerTests {
  @GameTest(template = "test_empty", timeoutTicks = 300, batch = "sublevel_hunger")
  public static void hunger_drains_pauses_persists_and_starves_to_death(GameTestHelper helper) {
    var level = helper.getLevel();
    var origin = new BlockPos(960, 80, 128);
    for (int x = 59; x <= 60; x++) {
      for (int z = 7; z <= 8; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    for (int x = -4; x <= 4; x++) {
      for (int z = -4; z <= 4; z++) {
        level.setBlock(
            origin.offset(x, -2, z),
            net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),
            Block.UPDATE_ALL);
      }
    }
    helper.runAfterDelay(
        10,
        () -> {
          level.setBlock(
              origin, ComponentRegistry.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
          var body =
              SubLevelAssemblyHelper.assembleBlocks(
                  level, origin, List.of(origin), new BoundingBox3i(origin, origin));
          CoreAssemblyRules.mark(level, body.getPlot().getCenterBlock());
          helper.assertTrue(SubLevelData.hunger(body) == 5, "New grid did not start at 5 hunger");
          SubLevelHunger.advance(body, SubLevelHunger.DRAIN_INTERVAL - 1);
          helper.assertTrue(SubLevelData.hunger(body) == 5, "Hunger drained before one minute");
          DevelopmentLifeSupport.pause(body);
          var paused = SubLevelData.hungerState(body);
          var saved = body.getUserDataTag().copy();
          body.setUserDataTag(saved.copy());
          helper.assertTrue(
              SubLevelData.hungerState(body).equals(paused), "Saved hunger timers changed");
          SubLevelHunger.advance(body, 100000);
          helper.assertTrue(
              SubLevelData.hungerState(body).equals(paused), "Paused hunger advanced");
          SubLevelData.hungerPaused(body, false);
          SubLevelHunger.advance(body, 1);
          helper.assertTrue(
              SubLevelData.hunger(body) == 4, "Resume lost the saved drain remainder");
          SubLevelHunger.advance(body, 4 * SubLevelHunger.DRAIN_INTERVAL);
          helper.assertTrue(
              SubLevelData.hunger(body) == 0 && SubLevelHealth.status(body).current() == 10,
              "Empty hunger damaged early or failed to clamp");
          DevelopmentLifeSupport.pause(body);
          SubLevelHunger.advance(body, 100000);
          helper.assertTrue(
              SubLevelHealth.status(body).current() == 10, "Pause did not stop starvation");
          SubLevelData.hungerPaused(body, false);
          // Observe the real server tick subscription and native starvation damage source.
          helper.runAfterDelay(
              81,
              () -> {
                helper.assertTrue(
                    SubLevelHealth.status(body).current() == 9,
                    "Native starvation tick did not deal exactly 1 HP");
                SubLevelData.hunger(body, 3);
                helper.runAfterDelay(
                    100,
                    () -> {
                      helper.assertTrue(
                          SubLevelHealth.status(body).current() == 9,
                          "Restoring hunger failed to stop starvation");
                      var oldHealth = SubLevelHealth.status(body);
                      SubLevelData.trust(body, 42);
                      var legacy = body.getUserDataTag().copy();
                      var data = legacy.getCompound(SubLevelData.STATE);
                      data.putInt("version", 1);
                      data.putInt("hunger", 20);
                      data.remove("hungerPaused");
                      data.remove("hungerDrainTicks");
                      data.remove("starvationTicks");
                      body.setUserDataTag(legacy);
                      helper.assertTrue(
                          SubLevelData.hunger(body) == 5
                              && SubLevelData.trust(body) == 42
                              && SubLevelHealth.status(body).equals(oldHealth),
                          "Migration lost shared state or failed to clamp old hunger");
                      SubLevelHealth.damage(body, 8, "test_setup");
                      SubLevelData.hunger(body, 0);
                      var target = CombatAdapter.target(body);
                      target.invulnerableTime = 0;
                      SubLevelHunger.advance(body, SubLevelHunger.STARVATION_INTERVAL);
                      helper.assertTrue(
                          SubLevelHealth.status(body).dead()
                              && SubLevelHealth.status(body).current() == 0
                              && SubLevelHealth.status(body).causeOfDeath().equals("starve"),
                          "Starvation failed to kill permanently or record its cause");
                      SubLevelData.hunger(body, 5);
                      SubLevelHunger.advance(body, 100000);
                      helper.assertTrue(
                          SubLevelHealth.status(body).dead(), "Hunger change revived a corpse");
                      helper.succeed();
                    });
              });
        });
  }
}
