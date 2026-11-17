package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.creature.survival.EscapeScoring.Threat;
import org.Azkerash.create_bioships.creature.survival.ThreatProfile;

/** Short tactical observations, separate from long-lived place knowledge. */
final class ThreatObservations {
  private record Seen(Threat threat, long until) {}

  private final Map<UUID, Seen> seen = new LinkedHashMap<>();
  private long next;
  private Vec3 explosive;

  static ThreatProfile profile(Entity entity) {
    boolean ranged =
        entity instanceof RangedAttackMob
            || entity instanceof CrossbowAttackMob
            || entity instanceof Witch
            || entity instanceof Guardian
            || entity instanceof Blaze
            || entity instanceof Ghast;
    if (entity instanceof LivingEntity living)
      ranged |=
          living.isHolding(Items.BOW)
              || living.isHolding(Items.CROSSBOW)
              || living.isHolding(Items.TRIDENT);
    return new ThreatProfile(
        entity instanceof Creeper || entity instanceof PrimedTnt,
        ranged,
        entity instanceof Spider,
        entity instanceof FlyingMob
            || entity instanceof Blaze
            || entity instanceof Vex
            || entity instanceof Phantom,
        entity instanceof Guardian || entity instanceof Drowned,
        entity instanceof Slime || entity instanceof Ravager);
  }

  List<Threat> update(LivingCoreBlockEntity core, Vec3 here) {
    long now = core.getLevel().getGameTime();
    seen.values().removeIf(s -> s.until <= now);
    if (now >= next) {
      next = now + 10;
      explosive = null;
      var entities =
          core.getLevel()
              .getEntities(
                  (Entity) null,
                  new AABB(here, here).inflate(32),
                  e ->
                      e.isAlive()
                          && (e instanceof PrimedTnt
                              || e instanceof LivingEntity living
                                  && (BioshipDefense.threatening(core, living)
                                      || e instanceof Creeper && e.distanceToSqr(here) < 144)));
      entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(here)));
      for (var entity : entities.stream().limit(8).toList()) {
        var point = entity.getBoundingBox().getCenter();
        if (!core.vision().visible(core, point) && entity.distanceToSqr(here) > 16) continue;
        var p = profile(entity);
        seen.put(
            entity.getUUID(), new Seen(new Threat(PlaceObservations.point(point), p), now + 60));
        if (entity instanceof PrimedTnt && entity.distanceToSqr(here) < 144 && explosive == null)
          explosive = point;
      }
      // Never retain more tactical entries than the live observation budget.
      while (seen.size() > 8) seen.remove(seen.keySet().iterator().next());
    }
    return seen.values().stream().map(Seen::threat).toList();
  }

  Vec3 explosive() {
    return explosive;
  }
}
