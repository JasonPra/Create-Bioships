package org.Azkerash.create_bioships;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Continuous vanilla-style green status particles, independent of trust bursts. */
final class BioshipPoisonFeedback {
  static boolean active(LivingCoreBlockEntity core) {
    return core.ship() != null && !core.body().dead() && core.poisonSeconds() > 0;
  }

  static void tick(LivingCoreBlockEntity core) {
    if (!active(core)
        || !(core.getLevel() instanceof ServerLevel level)
        || level.getGameTime() % 8 != 0) return;
    var points = BioshipParticleSurface.points(core);
    if (points.isEmpty()) return;
    for (int i = 0; i < 2; i++) {
      var p = points.get(level.random.nextInt(points.size()));
      level.sendParticles(
          ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xff4e9331),
          p.x,
          p.y,
          p.z,
          1,
          .1,
          .1,
          .1,
          .01);
    }
  }
}
