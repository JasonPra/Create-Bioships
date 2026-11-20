package org.Azkerash.create_bioships.development.testing;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import org.Azkerash.create_bioships.Create_bioships;

@GameTestHolder(Create_bioships.MODID)
@PrefixGameTestTemplate(false)
public final class CombatTimeControlsTests {
  @GameTest(template = "test_empty", timeoutTicks = 140, batch = "combat_time_controls")
  public static void hologram_controls_keep_results_and_current_deadline(GameTestHelper helper) {
    var level = helper.getLevel();
    var state = CoreTestState.get(level);
    var previousOrigin = state.combatOrigin;
    boolean previousBuilt = state.combatBuilt;
    var previousResults = List.copyOf(state.combatResults);
    state.combatOrigin = new BlockPos(560, 80, 128);
    for (int x = 34; x <= 36; x++) {
      for (int z = 6; z <= 9; z++) {
        level.setChunkForced(x, z, true);
      }
    }
    helper
        .startSequence()
        .thenWaitUntil(
            () ->
                helper.assertTrue(
                    level.isPositionEntityTicking(state.combatOrigin.north(16)),
                    "Waiting for control chunks"))
        .thenExecute(
            () -> {
              state.combatBuilt = true;
              state.combatTrialSeconds = 10;
              state.combatResults.add(
                  "minecraft:drowned: FAIL (no lethal attack within 10s); target acquired");
              var expectedResults = List.copyOf(state.combatResults);
              var player =
                  new net.neoforged.neoforge.common.util.FakePlayer(
                      level,
                      new com.mojang.authlib.GameProfile(
                          UUID.randomUUID(), "timeout-control-test"));
              CombatFixture.adjustTrialDuration(level, 0);
              state.combatRunning = true;
              state.combatActiveTrialSeconds = 10;
              state.combatDeadline = 12345;
              var plus = findButton(level, ".time_increase");
              helper.assertTrue(plus != null, "Increase interaction missing from hologram");
              var click =
                  new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, plus);
              CombatFixture.failureClick(click);
              helper.assertTrue(
                  click.isCanceled() && state.combatTrialSeconds == 15,
                  "Hologram plus did not add five seconds");
              helper.assertTrue(
                  state.combatDeadline == 12345 && state.combatActiveTrialSeconds == 10,
                  "Changing setting rewrote active trial");
              var minus = findButton(level, ".time_decrease");
              helper.assertTrue(minus != null, "Decrease interaction missing from hologram");
              CombatFixture.failureClick(
                  new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, minus));
              helper.assertTrue(
                  state.combatTrialSeconds == 10, "Hologram minus did not subtract five seconds");
              CombatFixture.adjustTrialDuration(level, -1000);
              helper.assertTrue(state.combatTrialSeconds == 5, "Minimum duration was not clamped");
              CombatFixture.adjustTrialDuration(level, 1000);
              helper.assertTrue(
                  state.combatTrialSeconds == 120, "Maximum duration was not clamped");
              var saved = state.save(new CompoundTag(), level.registryAccess());
              helper.assertTrue(
                  saved.getInt("CombatTrialSeconds") == 120
                      && saved.getInt("CombatActiveTrialSeconds") == 10,
                  "Duration setting was not saved separately from current trial");
              helper.assertTrue(
                  state.combatResults.equals(expectedResults), "Controls changed saved outcomes");
              helper.assertTrue(
                  new CoreTestState().combatTrialSeconds == 10,
                  "Old saves no longer default to ten seconds");
              state.combatRunning = false;
              state.combatDeadline = 0;
              state.combatTrialSeconds = 10;
              state.combatOrigin = previousOrigin;
              state.combatBuilt = previousBuilt;
              state.combatResults.clear();
              state.combatResults.addAll(previousResults);
              var cleanup = new ArrayList<Entity>();
              for (var entity : level.getAllEntities()) {
                if (entity.getTags().stream()
                    .anyMatch(
                        t ->
                            t.startsWith("create_bioships.combat_fixture.failure_")
                                || t.startsWith("create_bioships.combat_fixture.time_"))) {
                  cleanup.add(entity);
                }
              }
              cleanup.forEach(Entity::discard);
              helper.succeed();
            });
  }

  private static Entity findButton(net.minecraft.server.level.ServerLevel level, String suffix) {
    for (var entity : level.getAllEntities()) {
      if (!entity.isRemoved()
          && entity.getTags().contains("create_bioships.combat_fixture" + suffix)) {
        return entity;
      }
    }
    return null;
  }
}
