package org.Azkerash.create_bioships;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.Azkerash.create_bioships.registry.Create_bioships;

@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipHostility {
  private static final java.util.Set<Mob> ATTACHED =
      java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

  @SubscribeEvent
  public static void joined(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide
        && event.getEntity() instanceof Mob mob
        && mob instanceof Enemy
        && ATTACHED.add(mob))
      mob.targetSelector.addGoal(
          3,
          new NearestAttackableTargetGoal<>(
              mob,
              BioshipTargetEntity.class,
              10,
              true,
              false,
              e ->
                  e instanceof BioshipTargetEntity target
                      && target.core() != null
                      && !target.core().body().dead()));
  }
}
