package org.azkerash.createbioships.core.vision;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.combat.SubLevelCombatTarget;
import org.azkerash.createbioships.core.*;

/**
 * Angular broad phase and target-specific native ray casts; debug ray spacing cannot hide targets.
 */
public final class VisionDetection {
  public static final int TARGETS_PER_REFRESH = 16;

  public record Observation(UUID entity, UUID creature, Vec3 point, long tick) {
    public UUID key() {
      return creature == null ? entity : creature;
    }
  }

  public record Scan(List<Observation> observations, int next) {}

  private VisionDetection() {}

  public static boolean inside(Vec3 origin, Vec3 forward, Vec3 point, double range) {
    var delta = point.subtract(origin);
    double distance = delta.lengthSqr(), dot = delta.dot(forward);
    return distance <= range * range && dot >= 0 && dot * dot >= distance * .5;
  }

  private static Vec3 closest(AABB box, Vec3 origin) {
    return new Vec3(
        Math.clamp(origin.x, box.minX, box.maxX),
        Math.clamp(origin.y, box.minY, box.maxY),
        Math.clamp(origin.z, box.minZ, box.maxZ));
  }

  private static List<Vec3> samples(LivingEntity entity, Vec3 origin) {
    return List.of(
        closest(entity.getBoundingBox(), origin),
        entity.getEyePosition(),
        entity.getBoundingBox().getCenter());
  }

  private static UUID creature(LivingEntity entity) {
    if (entity instanceof SubLevelCombatTarget target && target.body() != null) {
      return SubLevelCreatureParts.root(target.body()).getUniqueId();
    }
    return null;
  }

  public static Scan scan(
      ServerSubLevel body,
      Vec3 origin,
      Vec3 forward,
      double range,
      int cursor,
      List<Observation> previous) {
    var owner = SubLevelCreatureParts.root(body);
    var candidates = new TreeMap<UUID, LivingEntity>();
    for (var entity :
        body.getLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(origin, origin).inflate(range),
                e -> e.isAlive() && !e.isSpectator())) {
      var construct = creature(entity);
      if (entity instanceof SubLevelCombatTarget target
          && (target.body() == null
              || owner.getUniqueId().equals(construct)
              || SubLevelHealth.status(target.body()).dead())) {
        continue;
      }
      if (samples(entity, origin).stream().noneMatch(p -> inside(origin, forward, p, range))) {
        continue;
      }
      candidates.putIfAbsent(construct == null ? entity.getUUID() : construct, entity);
    }
    long now = body.getLevel().getGameTime();
    var observations = new LinkedHashMap<UUID, Observation>();
    for (var seen : previous) {
      if (now - seen.tick() <= 40 && candidates.containsKey(seen.key())) {
        observations.put(seen.key(), seen);
      }
    }
    var entries = new ArrayList<>(candidates.entrySet());
    int count = Math.min(TARGETS_PER_REFRESH, entries.size());
    for (int i = 0; i < count; i++) {
      var candidate = entries.get(Math.floorMod(cursor + i, entries.size()));
      var entity = candidate.getValue();
      Observation visible = null;
      for (var point : samples(entity, origin)) {
        if (!inside(origin, forward, point, range)) {
          continue;
        }
        var delta = point.subtract(origin);
        double distance = delta.length();
        var ray =
            distance < 1e-6
                ? new VisionRaycaster.Ray(origin, point, false, false)
                : VisionRaycaster.cast(body, origin, delta.scale(1 / distance), distance);
        boolean hitCreature =
            entity instanceof SubLevelCombatTarget target
                && target.body() != null
                && ray.hitBody() != null
                && SubLevelCreatureParts.members(target.body()).stream()
                    .anyMatch(part -> part.getUniqueId().equals(ray.hitBody()));
        if (!ray.unloaded()
            && (!ray.blocked()
                || hitCreature
                || entity.getBoundingBox().inflate(.05).contains(ray.to()))) {
          visible = new Observation(entity.getUUID(), creature(entity), ray.to(), now);
          break;
        }
      }
      if (visible == null) {
        observations.remove(candidate.getKey());
      } else {
        observations.put(candidate.getKey(), visible);
      }
    }
    return new Scan(
        List.copyOf(observations.values()),
        entries.isEmpty() ? 0 : Math.floorMod(cursor + count, entries.size()));
  }
}
