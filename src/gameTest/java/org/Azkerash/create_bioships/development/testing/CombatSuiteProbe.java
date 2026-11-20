package org.Azkerash.create_bioships.development.testing;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;

/** Opt-in unattended run of the same visible fixture used by developers. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CombatSuiteProbe {
  private static int ticks, printed;
  private static String diagnostic = "";
  private static boolean acquired;

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!Boolean.getBoolean("core.combatSuiteProbe")) {
      return;
    }
    var level = event.getServer().overworld();
    ticks++;
    if (ticks == 20) {
      if (!DevelopmentWorldSetup.isDevelopment(level)) {
        throw new IllegalStateException("Not the development preset");
      }
      CombatFixture.reset(level);
      CoreTestState.get(level)
          .setCombatTrialSeconds(Integer.getInteger("core.combatProbeSeconds", 10));
      CombatFixture.start(level);
      event.getServer().tickRateManager().setTickRate(1000);
      System.out.println(
          "COMBAT_SUITE_BEGIN enemies=" + CoreTestState.get(level).getCombatEnemies().size());
    }
    if (ticks < 20) {
      return;
    }
    var state = CoreTestState.get(level);
    while (printed < state.getCombatResults().size()) {
      System.out.println(
          "COMBAT_SUITE_RESULT "
              + state.getCombatResults().get(printed++)
              + " acquired="
              + acquired
              + " "
              + diagnostic);
      acquired = false;
      diagnostic = "";
    }
    if (ticks % 10 == 0 && state.isCombatRunning()) {
      for (var entity : level.getAllEntities()) {
        if (entity instanceof net.minecraft.world.entity.Mob mob
            && mob.getTags().contains("create_bioships.combat_fixture")) {
          var target = mob.getTarget();
          if (target instanceof org.Azkerash.create_bioships.combat.SubLevelCombatTarget) {
            acquired = true;
          }
          diagnostic =
              "actual="
                  + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(
                      mob.getType())
                  + " at="
                  + mob.position()
                  + " target="
                  + (target == null ? "none" : target.position())
                  + " los="
                  + (target != null && mob.hasLineOfSight(target))
                  + " phase="
                  + (mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon d
                      ? d.getPhaseManager().getCurrentPhase().getPhase()
                      : "-")
                  + " navDone="
                  + mob.getNavigation().isDone()
                  + " goals="
                  + mob.goalSelector.getAvailableGoals().stream()
                      .filter(g -> g.isRunning())
                      .map(g -> g.getGoal().getClass().getSimpleName())
                      .toList();
        }
      }
    }
    if (ticks % 2000 == 0) {
      System.out.println("COMBAT_SUITE_PROGRESS " + CombatFixture.status(level));
    }
    if (!state.isCombatRunning()) {
      System.out.println("COMBAT_SUITE_DONE results=" + state.getCombatResults().size());
      event.getServer().halt(false);
    }
    if (ticks > 100000) {
      throw new IllegalStateException("Combat suite stuck");
    }
  }
}
