package org.azkerash.createbioships.core.vision;

import java.util.*;
import net.minecraft.world.phys.*;

/** Creature-wide target lock. Eye count changes vantage choices, never the shared cast budget. */
public final class OpticalTargeting {
  public static final int CASTS_PER_UPDATE = 2;
  private static final int BLOCKED_RETRY_TICKS = 10;
  private static final double VISIBILITY_EPSILON_BLOCKS = .05;
  private static final double LOCK_HYSTERESIS = .1;

  public record Key(UUID body, SubLevelVision.Eye eye) {}

  public record Sensor(Key key, Vec3 origin, Vec3 forward, double range, boolean aquatic) {}

  public record Candidate(UUID entity, UUID creature, AABB bounds, Vec3 eye, Set<UUID> members) {
    public Candidate {
      members = Set.copyOf(members);
    }

    public UUID key() {
      return creature == null ? entity : creature;
    }
  }

  public record Lock(
      Sensor sensor, Candidate target, Vec3 aim, VisionRaycaster.Ray ray, boolean visible) {}

  @FunctionalInterface
  public interface RayQuery {
    VisionRaycaster.Ray cast(Sensor sensor, Vec3 point);
  }

  private record Choice(Sensor sensor, Candidate target, Vec3 point, double score) {}

  private record Failure(UUID target, long until) {}

  private long lastUpdate = Long.MIN_VALUE;
  private UUID target;
  private List<Lock> locks = List.of();
  private final Map<Key, Failure> blocked = new HashMap<>();

  public List<Lock> update(
      long tick, List<Sensor> sensors, List<Candidate> candidates, RayQuery query) {
    var byTarget = new HashMap<UUID, Candidate>();
    for (var candidate : candidates) {
      byTarget.put(candidate.key(), candidate);
    }
    var currentSensors = new HashMap<Key, Sensor>();
    var keys = new HashSet<Key>();
    for (var sensor : sensors) {
      keys.add(sensor.key());
      currentSensors.put(sensor.key(), sensor);
    }
    blocked.keySet().retainAll(keys);
    if (lastUpdate == tick) {
      // An explicit refresh cannot spend the creature's budget twice in the same tick.
      locks =
          locks.stream()
              .filter(
                  lock ->
                      keys.contains(lock.sensor().key())
                          && byTarget.containsKey(lock.target().key())
                          && aim(
                                  currentSensors.get(lock.sensor().key()),
                                  byTarget.get(lock.target().key()))
                              != null)
              .toList();
      return locks;
    }
    lastUpdate = tick;
    var choices =
        target == null || !byTarget.containsKey(target)
            ? List.<Choice>of()
            : choices(sensors, byTarget.get(target), tick);
    if (choices.isEmpty()) {
      List<Choice> best = List.of();
      for (var candidate : candidates) {
        var possible = choices(sensors, candidate, tick);
        if (!possible.isEmpty()
            && (best.isEmpty() || possible.getFirst().score() > best.getFirst().score())) {
          best = possible;
        }
      }
      choices = best;
    }
    if (choices.isEmpty()) {
      target = null;
      locks = List.of();
      return locks;
    }
    target = choices.getFirst().target().key();
    var result = new ArrayList<Lock>();
    for (var choice : choices.subList(0, Math.min(CASTS_PER_UPDATE, choices.size()))) {
      var ray = query.cast(choice.sensor(), choice.point());
      boolean visible =
          !ray.unloaded()
              && (ray.from().distanceTo(ray.to()) + VISIBILITY_EPSILON_BLOCKS
                      >= ray.from().distanceTo(choice.point())
                  || ray.hitBody() != null && choice.target().members().contains(ray.hitBody())
                  || choice
                      .target()
                      .bounds()
                      .inflate(VISIBILITY_EPSILON_BLOCKS)
                      .contains(ray.to()));
      result.add(new Lock(choice.sensor(), choice.target(), choice.point(), ray, visible));
      if (visible) {
        blocked.remove(choice.sensor().key());
      } else {
        blocked.put(choice.sensor().key(), new Failure(target, tick + BLOCKED_RETRY_TICKS));
      }
    }
    locks = List.copyOf(result);
    return locks;
  }

  private List<Choice> choices(List<Sensor> sensors, Candidate candidate, long tick) {
    var result = new ArrayList<Choice>();
    for (var sensor : sensors) {
      var failure = blocked.get(sensor.key());
      if (failure != null && failure.target().equals(candidate.key()) && tick <= failure.until()) {
        continue;
      }
      var point = aim(sensor, candidate);
      if (point == null) {
        continue;
      }
      var delta = point.subtract(sensor.origin());
      double distance = delta.length();
      double alignment = distance < 1e-8 ? 1 : delta.dot(sensor.forward()) / distance;
      double score = alignment * 2 - distance / Math.max(1, sensor.range());
      if (locks.stream()
          .anyMatch(
              lock ->
                  lock.visible()
                      && lock.sensor().key().equals(sensor.key())
                      && lock.target().key().equals(candidate.key()))) {
        score += LOCK_HYSTERESIS;
      }
      result.add(new Choice(sensor, candidate, point, score));
    }
    result.sort(
        Comparator.comparingDouble(Choice::score)
            .reversed()
            .thenComparing(choice -> choice.sensor().key().body())
            .thenComparingLong(choice -> choice.sensor().key().eye().cell().asLong())
            .thenComparingInt(choice -> choice.sensor().key().eye().face().ordinal()));
    return result;
  }

  private static Vec3 aim(Sensor sensor, Candidate candidate) {
    if (VisionDetection.inside(
        sensor.origin(), sensor.forward(), candidate.eye(), sensor.range())) {
      return candidate.eye();
    }
    var box = candidate.bounds();
    var closest =
        new Vec3(
            Math.clamp(sensor.origin().x, box.minX, box.maxX),
            Math.clamp(sensor.origin().y, box.minY, box.maxY),
            Math.clamp(sensor.origin().z, box.minZ, box.maxZ));
    if (VisionDetection.inside(sensor.origin(), sensor.forward(), closest, sensor.range())) {
      return closest;
    }
    var center = box.getCenter();
    return VisionDetection.inside(sensor.origin(), sensor.forward(), center, sensor.range())
        ? center
        : null;
  }
}
