package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.CombatAdapter;

/** Temporary fixed-rate metabolism; saved state belongs to SubLevelData, not the timing policy. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class SubLevelHunger {
  public static final int DRAIN_INTERVAL = 20 * 60, STARVATION_INTERVAL = 20 * 4;

  private SubLevelHunger() {}

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      if (level.getGameTime() % 20 != 0) continue;
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body && CoreAssemblyRules.isCreature(body))
          advance(body, 20);
    }
  }

  /** Advances loaded simulation time only; paused/unloaded creatures accrue no catch-up debt. */
  public static void advance(ServerSubLevel body, int ticks) {
    if (ticks <= 0 || body.isRemoved()) return;
    var state = SubLevelData.hungerState(body);
    if (state.paused() || SubLevelHealth.status(body).dead()) return;
    int value = state.value();
    int drain = Math.clamp(state.drainTicks(), 0, DRAIN_INTERVAL - 1);
    int starvation = Math.clamp(state.starvationTicks(), 0, STARVATION_INTERVAL - 1);
    while (ticks > 0 && !SubLevelHealth.status(body).dead()) {
      if (value > 0) {
        int spent = Math.min(ticks, DRAIN_INTERVAL - drain);
        ticks -= spent;
        drain += spent;
        starvation = 0;
        if (drain >= DRAIN_INTERVAL) {
          value--;
          drain = 0;
        }
      } else {
        int spent = Math.min(ticks, STARVATION_INTERVAL - starvation);
        ticks -= spent;
        starvation += spent;
        if (starvation >= STARVATION_INTERVAL) {
          starvation = 0;
          var target = CombatAdapter.target(body);
          if (target != null) target.hurt(body.getLevel().damageSources().starve(), 1);
        }
      }
    }
    SubLevelData.hungerState(body, new SubLevelData.Hunger(value, false, drain, starvation));
  }
}
