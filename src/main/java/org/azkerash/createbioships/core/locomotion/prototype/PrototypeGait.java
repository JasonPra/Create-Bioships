package org.azkerash.createbioships.core.locomotion.prototype;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** World-anchored feet and alternating step groups, independent of the native walker planner. */
public final class PrototypeGait {
  public record Leg(
      BlockPos socket,
      Vec3 hip,
      Vec3 outward,
      double height,
      double coxa,
      double thigh,
      double shin,
      int group,
      LegMotionProfile profile) {
    public Leg(
        BlockPos socket,
        Vec3 hip,
        Vec3 outward,
        double height,
        double coxa,
        double thigh,
        double shin,
        int group) {
      this(socket, hip, outward, height, coxa, thigh, shin, group, LegMotionProfile.GENERIC);
    }
  }

  public record Contact(Vec3 point, UUID support, Vec3 localPoint) {}

  public interface Terrain {
    Contact probe(Vec3 desired, double height);

    Vec3 position(Contact contact);

    default List<Vec3> solve(Leg leg, Vec3 target) {
      return PrototypeLegKinematics.solve(
          leg.hip, leg.outward, target, leg.coxa, leg.thigh, leg.shin);
    }

    default double mobility(Leg leg) {
      return 1;
    }
  }

  private static final int CONTACT_RECHECK_TICKS = 10;
  private static final int RETRY_TICKS = 5;
  private static final double MIN_STRIDE_ERROR = .28;
  private static final double STEP_HEIGHT = .3;
  private static final double STILL_SPEED = .04;
  private static final double CONTACT_REACH_TOLERANCE = .15;
  private static final double RECHECK_HEIGHT = .3, RECHECK_DRIFT = .4;
  private static final double STRIDE_HEIGHT_FACTOR = .22, GROUP_STEP_THRESHOLD = .6;
  private static final double MAX_STEP_SECONDS = .24, MIN_STEP_SECONDS = .09;
  private static final double SPEED_STEP_FACTOR = .025, STRIDE_LEAD_FACTOR = .65;
  private static final double OBSTACLE_CLEARANCE = .15;
  private static final int TICKS_PER_SECOND = 20;
  private static final double REAR_PLANT_THRESHOLD_FACTOR = 1.4;
  private static final double FRONT_REACH_THRESHOLD_FACTOR = .75;
  private static final double MIN_REAR_SUPPORTED_DRIVE = .5;

  private static final class Foot {
    private Contact planted, destination;
    private Vec3 from, point;
    private long started, nextProbe;
    private int duration;
  }

  private final Map<BlockPos, Foot> feet = new HashMap<>();
  private long probes, steps;
  private Vec3 forward = new Vec3(0, 0, 1), center = Vec3.ZERO;
  private PrototypeStance.Surface surface = PrototypeStance.LEVEL;

  public void clear() {
    feet.clear();
    surface = PrototypeStance.LEVEL;
  }

  public void update(List<Leg> legs, Vec3 velocity, long tick, Terrain terrain) {
    update(
        legs,
        velocity,
        tick,
        terrain,
        velocity.horizontalDistance() > STILL_SPEED
            ? horizontal(velocity).normalize()
            : new Vec3(0, 0, 1));
  }

  public void update(List<Leg> legs, Vec3 velocity, long tick, Terrain terrain, Vec3 facing) {
    forward = horizontal(facing).normalize();
    center = Vec3.ZERO;
    for (var leg : legs) {
      center = center.add(leg.hip);
    }
    if (!legs.isEmpty()) {
      center = center.scale(1d / legs.size());
    }
    var present = new HashSet<BlockPos>();
    legs.forEach(l -> present.add(l.socket));
    feet.keySet().retainAll(present);
    double speed = velocity.horizontalDistance();
    int activeGroup = -1;
    for (var leg : legs) {
      var foot = feet.computeIfAbsent(leg.socket, ignored -> new Foot());
      if (foot.destination != null) {
        double alpha = Math.clamp((tick - foot.started) / (double) foot.duration, 0, 1);
        var target = terrain.position(foot.destination);
        if (target == null) {
          foot.destination = null;
          foot.planted = null;
        } else {
          double lift =
              Math.max(
                  STEP_HEIGHT * Math.sqrt(leg.height), target.y - foot.from.y + OBSTACLE_CLEARANCE);
          foot.point = foot.from.lerp(target, alpha).add(0, Math.sin(Math.PI * alpha) * lift, 0);
          if (alpha >= 1) {
            foot.planted = foot.destination;
            foot.destination = null;
            foot.nextProbe = tick + CONTACT_RECHECK_TICKS;
          } else {
            activeGroup = leg.group;
          }
        }
      }
      if (foot.destination == null && foot.planted != null) {
        foot.point = terrain.position(foot.planted);
        if (foot.point == null) {
          foot.planted = null;
        } else if (tick >= foot.nextProbe) {
          probes++;
          var verified = terrain.probe(foot.point, RECHECK_HEIGHT);
          foot.nextProbe = tick + CONTACT_RECHECK_TICKS;
          if (verified == null || verified.point.distanceTo(foot.point) > RECHECK_DRIFT) {
            foot.planted = null;
          } else {
            foot.planted = verified;
            foot.point = verified.point;
          }
        }
      }
      if (foot.planted == null && foot.destination == null && tick >= foot.nextProbe) {
        probes++;
        var contact = terrain.probe(ideal(leg, velocity, 0), leg.height + 1);
        foot.nextProbe = tick + RETRY_TICKS;
        if (contact != null && reachable(leg, contact.point, terrain)) {
          foot.planted = contact;
          foot.point = contact.point;
          foot.nextProbe = tick + CONTACT_RECHECK_TICKS + Math.floorMod(leg.socket.hashCode(), 3);
        }
      }
    }
    // A cached floor hit cannot support the body after the leg has moved beyond its reach.
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (foot.planted != null && !reachable(leg, foot.point, terrain)) {
        foot.planted = null;
      }
    }
    var planted =
        feet.values().stream()
            .filter(foot -> foot.planted != null)
            .map(foot -> foot.point)
            .toList();
    var measured = PrototypeStance.fit(planted, forward);
    if (measured.valid()) {
      surface = measured;
    } else if (planted.size() < 2) {
      surface = PrototypeStance.LEVEL;
    }
    boolean careful =
        legs.stream().anyMatch(PrototypeStance::adapts)
            && Math.abs(surface.grade()) > PrototypeStance.ADAPTATION_GRADE;
    if (careful) {
      // One foot swings at a time; retain the rear stance while the front reaches higher ground.
      if (activeGroup >= 0 || contacts() < Math.min(3, legs.size())) {
        return;
      }
      Leg chosen = null;
      double best = 1;
      for (var leg : legs) {
        var foot = feet.get(leg.socket);
        if (foot.planted == null || terrain.mobility(leg) <= 0) {
          continue;
        }
        boolean rear = leg.hip.subtract(center).dot(forward) < 0;
        double threshold = Math.max(MIN_STRIDE_ERROR, leg.height * STRIDE_HEIGHT_FACTOR);
        if (surface.grade() > 0) {
          threshold *= rear ? REAR_PLANT_THRESHOLD_FACTOR : FRONT_REACH_THRESHOLD_FACTOR;
        }
        double score =
            horizontal(foot.point.subtract(ideal(leg, velocity, 0))).length() / threshold;
        if (score > best) {
          chosen = leg;
          best = score;
        }
      }
      if (chosen != null && contacts() - 1 >= Math.min(3, legs.size() - 1)) {
        step(chosen, velocity, speed, tick, terrain);
      }
      return;
    }
    int selectedGroup = activeGroup;
    if (selectedGroup < 0) {
      double worst = 0;
      for (var leg : legs) {
        var foot = feet.get(leg.socket);
        if (foot.planted == null) {
          continue;
        }
        double error = horizontal(foot.point.subtract(ideal(leg, velocity, 0))).length();
        if (error > Math.max(MIN_STRIDE_ERROR, leg.height * STRIDE_HEIGHT_FACTOR)
            && error > worst) {
          worst = error;
          selectedGroup = leg.group;
        }
      }
    }
    if (selectedGroup < 0) {
      return;
    }
    int group = selectedGroup;
    long supports =
        legs.stream().filter(l -> l.group != group && feet.get(l.socket).planted != null).count();
    if (supports == 0) {
      return;
    }
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (leg.group != group
          || foot.destination != null
          || foot.planted == null
          || terrain.mobility(leg) <= 0) {
        continue;
      }
      double error = horizontal(foot.point.subtract(ideal(leg, velocity, 0))).length();
      if (error < MIN_STRIDE_ERROR * GROUP_STEP_THRESHOLD) {
        continue;
      }
      step(leg, velocity, speed, tick, terrain);
    }
  }

  private void step(Leg leg, Vec3 velocity, double speed, long tick, Terrain terrain) {
    var foot = feet.get(leg.socket);
    int duration =
        (int)
            Math.ceil(
                Math.clamp(
                        MAX_STEP_SECONDS - speed * SPEED_STEP_FACTOR,
                        MIN_STEP_SECONDS,
                        MAX_STEP_SECONDS)
                    * TICKS_PER_SECOND);
    double lead =
        Math.min(
            STRIDE_LEAD_FACTOR * leg.height,
            speed * (duration / (double) TICKS_PER_SECOND) * STRIDE_LEAD_FACTOR);
    probes++;
    var destination =
        terrain.probe(ideal(leg, velocity, speed > STILL_SPEED ? lead / speed : 0), leg.height + 1);
    if (destination != null && reachable(leg, destination.point, terrain)) {
      foot.from = foot.point;
      foot.planted = null;
      foot.destination = destination;
      foot.started = tick;
      foot.duration = duration;
      steps++;
    }
  }

  private static boolean reachable(Leg leg, Vec3 point, Terrain terrain) {
    var solved = terrain.solve(leg, point);
    return solved.getLast().distanceTo(point) <= CONTACT_REACH_TOLERANCE;
  }

  private static Vec3 horizontal(Vec3 v) {
    return new Vec3(v.x, 0, v.z);
  }

  private Vec3 ideal(Leg leg, Vec3 velocity, double leadSeconds) {
    double spread = (leg.coxa + leg.thigh + leg.shin) * .5;
    var side = horizontal(leg.outward).normalize();
    var footprint = leg.hip.add(side.scale(spread)).add(horizontal(velocity).scale(leadSeconds));
    return PrototypeStance.foot(leg, footprint, center, forward, surface.grade());
  }

  public List<Vec3> pose(Leg leg, double tick, Terrain terrain) {
    var foot = feet.get(leg.socket);
    var target =
        foot == null || foot.point == null
            ? ideal(leg, Vec3.ZERO, 0).add(0, -leg.height, 0)
            : foot.point;
    if (foot != null && foot.destination != null) {
      var end = terrain.position(foot.destination);
      if (end != null) {
        double alpha = Math.clamp((tick - foot.started) / foot.duration, 0, 1);
        double lift =
            Math.max(STEP_HEIGHT * Math.sqrt(leg.height), end.y - foot.from.y + OBSTACLE_CLEARANCE);
        target = foot.from.lerp(end, alpha).add(0, Math.sin(Math.PI * alpha) * lift, 0);
      }
    } else if (foot != null && foot.planted != null) {
      var planted = terrain.position(foot.planted);
      if (planted != null) {
        target = planted;
      }
    }
    return terrain.solve(leg, target);
  }

  public int contacts() {
    return (int) feet.values().stream().filter(f -> f.planted != null).count();
  }

  public double supportHeight(List<Leg> legs, double rootY) {
    double sum = 0;
    int count = 0;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (foot != null && foot.planted != null) {
        sum += foot.point.y + leg.height + rootY - leg.hip.y;
        count++;
      }
    }
    return count == 0 ? Double.NaN : sum / count;
  }

  public Vec3 bodyUp(List<Leg> legs) {
    return legs.stream().anyMatch(PrototypeStance::adapts)
        ? PrototypeStance.bodyUp(surface)
        : new Vec3(0, 1, 0);
  }

  public double rearSupport(List<Leg> legs) {
    int rear = 0, supported = 0;
    for (var leg : legs) {
      if (leg.hip.subtract(center).dot(forward) < 0) {
        rear++;
        var foot = feet.get(leg.socket);
        if (foot != null && foot.planted != null) {
          supported++;
        }
      }
    }
    return rear == 0 ? 1 : supported / (double) rear;
  }

  public double driveSupport(List<Leg> legs) {
    return surface.grade() > PrototypeStance.ADAPTATION_GRADE
            && legs.stream().anyMatch(leg -> leg.profile.rearLeverage())
        ? MIN_REAR_SUPPORTED_DRIVE + (1 - MIN_REAR_SUPPORTED_DRIVE) * rearSupport(legs)
        : 1;
  }

  public int requiredContacts(List<Leg> legs) {
    return Math.abs(surface.grade()) > PrototypeStance.ADAPTATION_GRADE
            && legs.stream().anyMatch(PrototypeStance::adapts)
        ? Math.max(Math.min(3, legs.size()), legs.size() / 2)
        : Math.max(2, legs.size() / 2);
  }

  public long getProbeCount() {
    return probes;
  }

  public long getStepCount() {
    return steps;
  }
}
