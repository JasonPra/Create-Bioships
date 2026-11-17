package org.Azkerash.create_bioships.combat;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.Azkerash.create_bioships.Create_bioships;

/** Automatic attachment of native targeting/attack support; no health or damage calculations. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class HostileCombatIntegration {
  private static final Set<Mob> ATTACHED = Collections.newSetFromMap(new WeakHashMap<>());

  private HostileCombatIntegration() {}

  public static boolean isHostile(Mob mob) {
    return mob.getType().getCategory() == MobCategory.MONSTER;
  }

  @SubscribeEvent
  public static void join(EntityJoinLevelEvent event) {
    if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !isHostile(mob))
      return;
    attach(mob);
  }

  public static void attach(Mob mob) {
    if (!ATTACHED.add(mob)) return;
    // The unused vanilla Giant has no attack goals of its own; use its native attack attributes.
    if (mob instanceof Giant giant)
      giant.goalSelector.addGoal(2, new MeleeAttackGoal(giant, 1, true));
    mob.targetSelector.addGoal(
        3,
        new NearestAttackableTargetGoal<>(mob, SubLevelCombatTarget.class, 5, true, false, null));
  }

  public static void updateBrainTarget(Mob mob, LivingEntity target) {
    var brain = mob.getBrain();
    if (!brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) return;
    if (target instanceof SubLevelCombatTarget) {
      brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
      if (mob instanceof net.minecraft.world.entity.monster.warden.Warden warden)
        warden.increaseAngerAt(target, 80, false);
      if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED))
        brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600);
    } else if (target == null
        && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)
            instanceof SubLevelCombatTarget) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
  }

  public static void tickBosses(ServerLevel level) {
    // Dragons tick their phase controller instead of Mob's goal selectors.
    for (var entity : level.getAllEntities())
      if (entity instanceof EnderDragon dragon && dragon.isAlive()) {
        if (dragon.getTarget() != null && !(dragon.getTarget() instanceof SubLevelCombatTarget))
          continue;
        var target =
            level
                .getEntitiesOfClass(
                    SubLevelCombatTarget.class,
                    dragon.getBoundingBox().inflate(64),
                    t -> t.isAlive() && dragon.hasLineOfSight(t))
                .stream()
                .min(Comparator.comparingDouble(dragon::distanceToSqr))
                .orElse(null);
        dragon.setTarget(target);
        if (target != null
            && (dragon.getPhaseManager().getCurrentPhase().getPhase()
                    == EnderDragonPhase.HOLDING_PATTERN
                || dragon.getPhaseManager().getCurrentPhase().getPhase()
                    == EnderDragonPhase.HOVERING)) {
          dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
          dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(target);
        }
      }
  }
}
