package org.azkerash.createbioships.core.trust;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.azkerash.createbioships.combat.SubLevelCombatTarget;
import org.azkerash.createbioships.core.*;

/** One player-care reward per creature, protected by a saved effect-length cooldown. */
public final class RegenerationTrust {
  public static final double REWARD = .5;
  public static final int MIN_INTERVAL = 60 * 20;

  private RegenerationTrust() {}

  public static void applied(
      ThrownPotion potion,
      LivingEntity recipient,
      MobEffectInstance effect,
      boolean accepted,
      boolean alreadyRegenerating) {
    if (!accepted
        || alreadyRegenerating
        || !(potion.getOwner() instanceof ServerPlayer)
        || !effect.is(MobEffects.REGENERATION)
        || !(recipient instanceof SubLevelCombatTarget target)) {
      return;
    }
    var body = target.body();
    if (body == null || !SubLevelCreatureParts.available(body)) {
      return;
    }
    body = SubLevelCreatureParts.root(body);
    var health = SubLevelHealth.status(body);
    long now = body.getLevel().getGameTime();
    if (!SubLevelData.isLiving(body)
        || health.dead()
        || health.current() >= health.maximum()
        || now < SubLevelData.regenerationTrustUntil(body)) {
      return;
    }
    var active = recipient.getEffect(MobEffects.REGENERATION);
    if (active == null) {
      return;
    }
    long until =
        active.isInfiniteDuration()
            ? Long.MAX_VALUE
            : now + Math.max(MIN_INTERVAL, active.getDuration());
    SubLevelData.regenerationTrustUntil(body, until);
    SubLevelTrustGains.add(body, REWARD);
  }
}
