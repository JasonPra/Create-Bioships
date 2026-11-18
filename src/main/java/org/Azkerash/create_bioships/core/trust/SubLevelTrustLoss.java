package org.Azkerash.create_bioships.core.trust;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.Azkerash.create_bioships.core.*;

/** Damage-driven trust policy; saved values and history belong to SubLevelData. */
public final class SubLevelTrustLoss {
  public static final double OWNER_DAMAGE_SCALE = 50, STARVATION_DAMAGE_SCALE = 20;
  public static final int OWNER_HIT_WINDOW = 30 * 20;
  private static final double[] FREQUENCY = {.05, .15, .35, .65, 1};

  private SubLevelTrustLoss() {}

  private static double healthLost(ServerSubLevel body, SubLevelData.Health before) {
    if (!SubLevelCreatureParts.available(body)
        || before.dead()
        || !Double.isFinite(before.maximum())
        || before.maximum() <= 0
        || !SubLevelData.isLiving(body)) return 0;
    return Math.clamp(
        before.current() - SubLevelHealth.status(body).current(), 0, before.current());
  }

  /** Accepted owner attacks are logged even when armor absorbs all health damage. */
  public static void ownerAttack(
      ServerSubLevel body,
      net.minecraft.world.damagesource.DamageSource source,
      SubLevelData.Health before) {
    if (!SubLevelCreatureParts.available(body)
        || before.dead()
        || before.maximum() <= 0
        || !SubLevelData.isLiving(body)) return;
    body = SubLevelCreatureParts.root(body);
    if (!(source.getEntity() instanceof net.minecraft.world.entity.player.Player player)
        || !player
            .getUUID()
            .equals(org.Azkerash.create_bioships.core.access.SubLevelPermissions.get(body).owner()))
      return;
    long now = body.getLevel().getGameTime();
    var history = new java.util.ArrayList<>(SubLevelData.ownerHits(body));
    int recent = 0;
    for (var hit : history) if (hit.tick() <= now && now - hit.tick() < OWNER_HIT_WINDOW) recent++;
    double factor = FREQUENCY[Math.min(FREQUENCY.length - 1, recent)];
    double lost = healthLost(body, before);
    history.add(new SubLevelData.OwnerHit(now, lost, player.getUUID()));
    SubLevelData.ownerHits(body, history);
    if (lost > 0)
      SubLevelData.trust(
          body, SubLevelData.trust(body) - OWNER_DAMAGE_SCALE * (lost / before.maximum()) * factor);
  }

  public static void starvation(ServerSubLevel body, SubLevelData.Health before) {
    double lost = healthLost(body, before);
    if (lost <= 0) return;
    double remaining = Math.clamp(SubLevelHealth.status(body).current() / before.maximum(), 0, 1);
    double penalty =
        STARVATION_DAMAGE_SCALE * (lost / before.maximum()) * (1 + 2 * (1 - remaining));
    SubLevelData.trust(body, SubLevelData.trust(body) - penalty);
  }
}
