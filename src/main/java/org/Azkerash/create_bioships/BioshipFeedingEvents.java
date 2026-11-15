package org.Azkerash.create_bioships;

import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

@EventBusSubscriber(modid = Create_bioships.MODID)
public final class BioshipFeedingEvents {
  private static final ThreadLocal<UUID> SWALLOWED = new ThreadLocal<>();

  public static boolean bite(
      LivingEntity prey, DamageSource source, float amount, boolean swallowed) {
    if (!swallowed || prey instanceof Player) return prey.hurt(source, amount);
    SWALLOWED.set(prey.getUUID());
    try {
      return prey.hurt(source, amount);
    } finally {
      SWALLOWED.remove();
    }
  }

  @SubscribeEvent
  public static void retainCorpse(LivingDropsEvent event) {
    if (event.getEntity().getUUID().equals(SWALLOWED.get())) event.setCanceled(true);
  }
}
