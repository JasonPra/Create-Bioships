package org.azkerash.createbioships.core.vision;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.combat.SubLevelCombatTarget;
import org.azkerash.createbioships.core.*;

/** Angular broad phase for shared medium-aware target queries; fan spacing cannot hide targets. */
public final class VisionDetection {
  public static final int TARGETS_PER_REFRESH = OpticalTargeting.CASTS_PER_UPDATE;

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

  private static UUID creature(LivingEntity entity) {
    if (entity instanceof SubLevelCombatTarget target && target.body() != null) {
      return SubLevelCreatureParts.root(target.body()).getUniqueId();
    }
    return null;
  }

  public static List<OpticalTargeting.Candidate> candidates(
      ServerSubLevel body, List<OpticalTargeting.Sensor> sensors) {
    if (sensors.isEmpty()) {
      return List.of();
    }
    var owner = SubLevelCreatureParts.root(body);
    AABB area = new AABB(sensors.getFirst().origin(), sensors.getFirst().origin());
    double range = 0;
    for (var sensor : sensors) {
      area = area.minmax(new AABB(sensor.origin(), sensor.origin()));
      range = Math.max(range, sensor.range());
    }
    var candidates = new TreeMap<UUID, OpticalTargeting.Candidate>();
    for (var entity :
        body.getLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                area.inflate(range),
                target -> target.isAlive() && !target.isSpectator())) {
      var construct = creature(entity);
      if (entity instanceof SubLevelCombatTarget target
          && (target.body() == null
              || owner.getUniqueId().equals(construct)
              || SubLevelHealth.status(target.body()).dead())) {
        continue;
      }
      UUID key = construct == null ? entity.getUUID() : construct;
      if (candidates.containsKey(key)) {
        continue;
      }
      var members = new HashSet<UUID>();
      if (entity instanceof SubLevelCombatTarget target && target.body() != null) {
        for (var part : SubLevelCreatureParts.members(target.body())) {
          members.add(part.getUniqueId());
        }
      }
      candidates.put(
          key,
          new OpticalTargeting.Candidate(
              entity.getUUID(),
              construct,
              entity.getBoundingBox(),
              entity.getEyePosition(),
              members));
    }
    return List.copyOf(candidates.values());
  }

  /** Compatibility query: all sensing goes through the root's shared two-ray coordinator. */
  public static Scan scan(
      ServerSubLevel body,
      Vec3 origin,
      Vec3 forward,
      double range,
      int cursor,
      List<Observation> previous) {
    SubLevelVision.refresh(body, SubLevelVision.EYES_PER_GRID);
    return new Scan(
        SubLevelVision.detected(body).stream()
            .filter(observation -> inside(origin, forward, observation.point(), range))
            .toList(),
        cursor);
  }
}
