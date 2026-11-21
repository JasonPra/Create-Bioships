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
      LegMotionProfile profile,
      double reach) {
    public Leg(
        BlockPos socket,
        Vec3 hip,
        Vec3 outward,
        double height,
        double coxa,
        double thigh,
        double shin,
        int group,
        LegMotionProfile profile) {
      this(
          socket,
          hip,
          outward,
          height,
          coxa,
          thigh,
          shin,
          group,
          profile,
          (coxa + thigh + shin) * .5);
    }

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

  public record Contact(Vec3 point, UUID support, Vec3 localPoint, Vec3 normal, Vec3 localNormal) {
    public Contact(Vec3 point, UUID support, Vec3 localPoint) {
      this(point, support, localPoint, new Vec3(0, 1, 0), new Vec3(0, 1, 0));
    }
  }

  public interface Terrain {
    Contact probe(Vec3 desired, double height);

    Vec3 position(Contact contact);

    default Contact probe(Leg leg, Vec3 desired, double height, Contact previous) {
      return probe(desired, height);
    }

    default Vec3 velocity(Contact contact) {
      return Vec3.ZERO;
    }

    default Vec3 normal(Contact contact) {
      return contact.normal();
    }

    default Vec3 up(Leg leg) {
      return new Vec3(0, 1, 0);
    }

    default List<Vec3> solve(Leg leg, Vec3 target) {
      return PrototypeLegKinematics.solve(
          leg.hip, leg.outward, target, leg.coxa, leg.thigh, leg.shin);
    }

    default List<Vec3> solveRecovery(Leg leg, Vec3 target) {
      return solve(leg, target);
    }

    default boolean clearPose(Leg leg, List<Vec3> points) {
      return true;
    }

    default Vec3 massCenter() {
      return null;
    }

    default double mobility(Leg leg) {
      return 1;
    }
  }

  private static final int TETRAPOD_LEGS = 8, TETRAPOD_SUPPORTS = 4;
  private static final double TETRAPOD_MAX_GRADE = .25, TETRAPOD_MIN_UP = .9;
  private static final double TETRAPOD_MAX_YAW_RATE = .15;
  private static final double CLIMB_STEP_LOOKAHEAD_SECONDS = .4, CLIMB_STEP_LEAD_BLOCKS = .5;
  private static final double CLIMB_FRONT_PRIORITY = 1.2;
  private static final int RECOVERY_STEP_TICKS = 12;
  private static final int CONTACT_RECHECK_TICKS = 10;
  private static final int RETRY_TICKS = 5;
  private static final int MIN_ANCHORED_STANCE_TICKS = 4;
  private static final double MIN_STRIDE_ERROR = .28;
  private static final double SAME_SURFACE_NORMAL_DOT = .99;
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
    private boolean recovery;
    private Vec3 from, point;
    private long started, nextProbe, nextStep;
    private int duration;
  }

  private final Map<BlockPos, Foot> feet = new HashMap<>();
  private long probes, steps;
  private Vec3 planeUp = new Vec3(0, 1, 0);
  private Vec3 forward = new Vec3(0, 0, 1), center = Vec3.ZERO;
  private double requestedYaw;
  private static final double TURN_FOOT_LEAD_SECONDS = .4;
  private PrototypeStance.Surface surface = PrototypeStance.LEVEL;
  private boolean supportReady;
  private int unsupportedTicks;
  private Vec3 weightShift;
  private static final double WEIGHT_SHIFT_SPEED = 1.5, WEIGHT_SHIFT_MARGIN = .04;
  private static final int BALANCE_SEARCH_STEPS = 8;
  private static final double WEIGHT_SHIFT_GAIN = 4, WALL_MAX_UP = .7, WALL_APPROACH_DOT = -.1;
  private static final int SUPPORT_TRANSFER_TICKS = 6;
  private static final double SUPPORT_LOOKAHEAD_SECONDS = .2;

  /** An edited socket must plant relative to its new rest angle, not retain a cached old toe. */
  public void resetLeg(BlockPos socket) {
    feet.remove(socket);
  }

  public void clear() {
    feet.clear();
    surface = PrototypeStance.LEVEL;
    supportReady = false;
    unsupportedTicks = 0;
    weightShift = null;
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
    update(legs, velocity, tick, terrain, facing, 0);
  }

  public void update(
      List<Leg> legs, Vec3 velocity, long tick, Terrain terrain, Vec3 facing, double yawRate) {
    requestedYaw =
        Math.clamp(
            yawRate,
            -org.azkerash.createbioships.core.locomotion.LegTurnDrive.MAX_YAW_RATE,
            org.azkerash.createbioships.core.locomotion.LegTurnDrive.MAX_YAW_RATE);
    planeUp =
        legs.stream()
            .filter(PrototypeGait::anchored)
            .findFirst()
            .map(terrain::up)
            .orElse(new Vec3(0, 1, 0))
            .normalize();
    forward = planar(facing).normalize();
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
    double speed = planar(velocity).length();
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
                  STEP_HEIGHT * Math.sqrt(leg.height),
                  target.subtract(foot.from).dot(planeUp) + OBSTACLE_CLEARANCE);
          foot.point =
              foot.from.lerp(target, alpha).add(planeUp.scale(Math.sin(Math.PI * alpha) * lift));
          if (alpha >= 1) {
            foot.planted = foot.destination;
            if (anchored(leg)) {
              // A surface may disappear during the swing. Do not attach to an obsolete landing.
              probes++;
              var verified = terrain.probe(leg, target, RECHECK_HEIGHT, foot.destination);
              foot.planted =
                  verified != null && verified.point.distanceTo(target) <= RECHECK_DRIFT
                      ? verified
                      : null;
            }
            foot.destination = null;
            if (anchored(leg)) {
              foot.nextStep = tick + MIN_ANCHORED_STANCE_TICKS;
            }
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
          var verified = terrain.probe(leg, foot.point, RECHECK_HEIGHT, foot.planted);
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
        var contact = terrain.probe(leg, ideal(leg, velocity, 0), leg.height + 1, null);
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
      if (foot.planted != null && !reachable(leg, foot.point, terrain, foot.recovery)) {
        foot.planted = null;
        if (anchored(leg)) {
          // Rotation across an edge can retire a foothold before its normal timer is due.
          // Try one replacement immediately instead of allowing stale retry timing to drop grip.
          probes++;
          var replacement = terrain.probe(leg, ideal(leg, velocity, 0), leg.height + 1, null);
          if (replacement != null && reachable(leg, replacement.point, terrain)) {
            foot.planted = replacement;
            foot.point = replacement.point;
          }
          foot.nextProbe = tick + RETRY_TICKS;
        }
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
    weightShift = null;
    var massCenter = terrain.massCenter();
    supportReady = supportsBody(legs, terrain, massCenter == null ? center : massCenter);
    unsupportedTicks = supportReady ? 0 : unsupportedTicks + 1;
    boolean tetrapod = stableTetrapod(legs, terrain);
    boolean careful =
        (legs.stream().anyMatch(PrototypeGait::anchored) && !tetrapod
            || (legs.stream().anyMatch(PrototypeStance::adapts)
                && Math.abs(surface.grade()) > PrototypeStance.ADAPTATION_GRADE));
    if (careful) {
      carefulStep(legs, velocity, speed, tick, terrain, activeGroup);
      return;
    }
    int selectedGroup = activeGroup;
    if (selectedGroup < 0) {
      double worst = 0;
      for (var leg : legs) {
        var foot = feet.get(leg.socket);
        if (foot.planted == null || tick < foot.nextStep) {
          continue;
        }
        double error = strideError(leg, foot, velocity, terrain);
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
    if (supports < (tetrapod ? TETRAPOD_SUPPORTS : 1)) {
      return;
    }
    if (tetrapod) {
      var supportPoints =
          legs.stream()
              .filter(leg -> leg.group != group)
              .map(leg -> feet.get(leg.socket))
              .filter(foot -> foot.planted != null)
              .map(foot -> foot.point)
              .toList();
      var supportedCenter = terrain.massCenter();
      if (!org.azkerash.createbioships.core.locomotion.SupportPolygon.containsProjection(
          supportedCenter == null ? center : supportedCenter, supportPoints)) {
        carefulStep(legs, velocity, speed, tick, terrain, activeGroup);
        return;
      }
    }
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (leg.group != group
          || foot.destination != null
          || foot.planted == null
          || tick < foot.nextStep
          || terrain.mobility(leg) <= 0) {
        continue;
      }
      double error = strideError(leg, foot, velocity, terrain);
      if (error < MIN_STRIDE_ERROR * GROUP_STEP_THRESHOLD) {
        continue;
      }
      step(leg, velocity, speed, tick, terrain);
    }
  }

  private void carefulStep(
      List<Leg> legs, Vec3 velocity, double speed, long tick, Terrain terrain, int activeGroup) {
    // One foot swings at a time; retain the rear stance while the front reaches higher ground.
    if (activeGroup >= 0 || contacts() < Math.min(3, legs.size())) {
      return;
    }
    Leg chosen = null, pending = null;
    double best = 1, pendingScore = 1;
    var massCenter = terrain.massCenter();
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (foot.planted == null || tick < foot.nextStep || terrain.mobility(leg) <= 0) {
        continue;
      }
      boolean rear = leg.hip.subtract(center).dot(forward) < 0;
      double threshold = Math.max(MIN_STRIDE_ERROR, leg.height * STRIDE_HEIGHT_FACTOR);
      if (surface.grade() > 0 && leg.profile.rearLeverage()) {
        threshold *= rear ? REAR_PLANT_THRESHOLD_FACTOR : FRONT_REACH_THRESHOLD_FACTOR;
      }
      double score = strideError(leg, foot, velocity, terrain) / threshold;
      if (anchored(leg)
          && planeUp.y < TETRAPOD_MIN_UP
          && leg.hip.subtract(center).dot(velocity) > 0) {
        score *= CLIMB_FRONT_PRIORITY;
      }
      if (anchored(leg) && massCenter != null && !balancedWithout(legs, leg, terrain, massCenter)) {
        if (score > pendingScore) {
          pending = leg;
          pendingScore = score;
        }
      } else if (score > best) {
        chosen = leg;
        best = score;
      }
    }
    if (chosen != null
        && contacts() - 1
            >= (anchored(chosen) ? requiredContacts(legs) : Math.min(3, legs.size() - 1))) {
      step(chosen, velocity, speed, tick, terrain);
    } else if (pending != null && supportReady && massCenter != null) {
      var remaining = plantedPositions(legs, terrain, pending);
      if (remaining.size() >= ArachnidBodyMotor.MIN_ANCHORS) {
        var centre = remaining.stream().reduce(Vec3.ZERO, Vec3::add).scale(1d / remaining.size());
        var normal = anchorNormal(legs, terrain);
        double outside = 0, inside = 1;
        for (int search = 0; search < BALANCE_SEARCH_STEPS; search++) {
          double fraction = (outside + inside) / 2;
          if (org.azkerash.createbioships.core.locomotion.SupportPolygon.containsProjection(
              massCenter.lerp(centre, fraction), remaining, normal)) {
            inside = fraction;
          } else {
            outside = fraction;
          }
        }
        var shift = ArachnidBodyMotor.tangent(centre.subtract(massCenter), normal);
        if (shift.lengthSqr() > 1e-8) {
          weightShift = shift.scale(inside).add(shift.normalize().scale(WEIGHT_SHIFT_MARGIN));
        }
      }
    }
  }

  private boolean balancedWithout(List<Leg> legs, Leg moving, Terrain terrain, Vec3 massCenter) {
    return org.azkerash.createbioships.core.locomotion.SupportPolygon.containsProjection(
        massCenter, plantedPositions(legs, terrain, moving), anchorNormal(legs, terrain));
  }

  private boolean stableTetrapod(List<Leg> legs, Terrain terrain) {
    if (legs.size() != TETRAPOD_LEGS
        || contacts() < TETRAPOD_SUPPORTS
        || Math.abs(surface.grade()) > TETRAPOD_MAX_GRADE
        || planeUp.y < TETRAPOD_MIN_UP
        || Math.abs(requestedYaw) > TETRAPOD_MAX_YAW_RATE) {
      return false;
    }
    int firstGroup = 0;
    for (var leg : legs) {
      if (!anchored(leg)) {
        return false;
      }
      if (leg.group == 0) {
        firstGroup++;
      }
      var foot = feet.get(leg.socket);
      if (foot.recovery
          || foot.planted != null && terrain.normal(foot.planted).y < TETRAPOD_MIN_UP) {
        return false;
      }
    }
    return firstGroup == TETRAPOD_SUPPORTS;
  }

  private double strideError(Leg leg, Foot foot, Vec3 velocity, Terrain terrain) {
    // Start a leading step before the body moves past the remaining support
    // triangle. Waiting for large hip drift can deadlock a four-legged climber.
    double lookahead =
        anchored(leg) && planeUp.y < TETRAPOD_MIN_UP ? CLIMB_STEP_LOOKAHEAD_SECONDS : 0;
    var difference = foot.point.subtract(ideal(leg, velocity, lookahead));
    // During an edge transfer one toe may be on a wall while the others remain on the floor.
    // Distance into that wall is body clearance, not a reason to immediately lift the new grip.
    return anchored(leg)
        ? ArachnidBodyMotor.tangent(difference, terrain.normal(foot.planted)).length()
        : planar(difference).length();
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
    if (anchored(leg) && planeUp.y < TETRAPOD_MIN_UP && speed > STILL_SPEED) {
      lead = Math.min(STRIDE_LEAD_FACTOR * leg.height, Math.max(lead, CLIMB_STEP_LEAD_BLOCKS));
    }
    probes++;
    var destination =
        terrain.probe(
            leg,
            ideal(leg, velocity, speed > STILL_SPEED ? lead / speed : 0),
            leg.height + 1,
            null);
    if (destination != null
        && anchored(leg)
        && destination.point.distanceTo(foot.point) < MIN_STRIDE_ERROR * GROUP_STEP_THRESHOLD
        && terrain.normal(destination).dot(terrain.normal(foot.planted))
            > SAME_SURFACE_NORMAL_DOT) {
      return;
    }
    if (destination != null && reachable(leg, destination.point, terrain)) {
      foot.from = foot.point;
      foot.planted = null;
      foot.destination = destination;
      foot.started = tick;
      foot.duration = duration;
      foot.recovery = false;
      steps++;
    }
  }

  private static boolean reachable(Leg leg, Vec3 point, Terrain terrain) {
    return reachable(leg, point, terrain, false);
  }

  private static boolean reachable(Leg leg, Vec3 point, Terrain terrain, boolean recovery) {
    var solved = recovery ? terrain.solveRecovery(leg, point) : terrain.solve(leg, point);
    return solved.getLast().distanceTo(point) <= CONTACT_REACH_TOLERANCE;
  }

  private static Vec3 horizontal(Vec3 v) {
    return new Vec3(v.x, 0, v.z);
  }

  private Vec3 ideal(Leg leg, Vec3 velocity, double leadSeconds) {
    double spread = leg.reach;
    var side = planar(leg.outward).normalize();
    var footprint = leg.hip.add(side.scale(spread)).add(planar(velocity).scale(leadSeconds));
    var offset = footprint.subtract(center);
    var lead = planeUp.cross(offset).scale(requestedYaw * TURN_FOOT_LEAD_SECONDS);
    return PrototypeStance.foot(leg, footprint.add(lead), center, forward, surface.grade());
  }

  /** Lift only the trapped foot and preserve the other planted supports. */
  public boolean recover(Leg leg, Vec3 obstacle, long tick, Terrain terrain) {
    var foot = feet.get(leg.socket());
    if (foot == null
        || foot.point == null
        || foot.destination != null
        || (anchored(leg) && contacts() <= 3)) {
      return false;
    }
    var target = FootholdRecovery.nearest(leg, foot.point, obstacle, terrain);
    if (target == null) {
      return false;
    }
    foot.from = foot.point;
    foot.planted = null;
    foot.destination = target;
    foot.started = tick;
    foot.duration = RECOVERY_STEP_TICKS;
    foot.recovery = true;
    steps++;
    return true;
  }

  public List<Vec3> pose(Leg leg, double tick, Terrain terrain) {
    var foot = feet.get(leg.socket);
    if (anchored(leg)
        && !supportReady
        && unsupportedTicks > SUPPORT_TRANSFER_TICKS
        && (foot == null || foot.destination == null)) {
      // Inadequate support must not pin kinematically posed bones to world-space
      // toes while the unsupported hull falls under Sable's gravity.
      return terrain.solve(leg, ideal(leg, Vec3.ZERO, 0).subtract(planeUp.scale(leg.height)));
    }
    var target =
        foot == null || foot.point == null
            ? ideal(leg, Vec3.ZERO, 0).subtract(planeUp.scale(leg.height))
            : foot.point;
    if (foot != null && foot.destination != null) {
      var end = terrain.position(foot.destination);
      if (end != null) {
        double alpha = Math.clamp((tick - foot.started) / foot.duration, 0, 1);
        double lift =
            Math.max(
                STEP_HEIGHT * Math.sqrt(leg.height),
                end.subtract(foot.from).dot(planeUp) + OBSTACLE_CLEARANCE);
        target = foot.from.lerp(end, alpha).add(planeUp.scale(Math.sin(Math.PI * alpha) * lift));
      }
    } else if (foot != null && foot.planted != null) {
      var planted = terrain.position(foot.planted);
      if (planted != null) {
        target = planted;
      }
    }
    return foot != null && foot.recovery
        ? terrain.solveRecovery(leg, target)
        : terrain.solve(leg, target);
  }

  public static boolean anchored(Leg leg) {
    return leg.profile.contact() == LegMotionProfile.GroundContact.ANCHORED;
  }

  private Vec3 planar(Vec3 value) {
    return value.subtract(planeUp.scale(value.dot(planeUp)));
  }

  /** Only planted adhesive toes count; ordinary crab contacts cannot supply wall adhesion. */
  public int anchoredContacts(List<Leg> legs) {
    return (int)
        legs.stream()
            .filter(PrototypeGait::anchored)
            .filter(leg -> feet.containsKey(leg.socket) && feet.get(leg.socket).planted != null)
            .count();
  }

  public int groundContacts(List<Leg> legs, Terrain terrain, double minimumUp) {
    int count = 0;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (foot != null && foot.planted != null && terrain.normal(foot.planted).y >= minimumUp) {
        count++;
      }
    }
    return count;
  }

  public Vec3 supportVelocity(List<Leg> legs, Terrain terrain) {
    var sum = Vec3.ZERO;
    int count = 0;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (anchored(leg) && foot != null && foot.planted != null) {
        sum = sum.add(terrain.velocity(foot.planted));
        count++;
      }
    }
    return count == 0 ? Vec3.ZERO : sum.scale(1d / count);
  }

  public Vec3 anchorNormal(List<Leg> legs, Terrain terrain) {
    var normal = Vec3.ZERO;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (anchored(leg) && foot != null && foot.planted != null) {
        normal = normal.add(terrain.normal(foot.planted));
      }
    }
    return normal.lengthSqr() > 1e-8 ? normal.normalize() : planeUp;
  }

  /**
   * Use the acquired obstacle face for travel while the body is still transferring off the floor.
   */
  public Vec3 movementNormal(List<Leg> legs, Terrain terrain, Vec3 intent) {
    var faces = Vec3.ZERO;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (anchored(leg) && foot != null && foot.planted != null) {
        var normal = terrain.normal(foot.planted);
        if (normal.y < WALL_MAX_UP && normal.dot(intent) < WALL_APPROACH_DOT) {
          faces = faces.add(normal);
        }
      }
    }
    return faces.lengthSqr() > 1e-8 ? faces.normalize() : anchorNormal(legs, terrain);
  }

  /** Contact-plane point for the acquired face; moving supports are resolved in world space. */
  public Vec3 surfacePoint(List<Leg> legs, Terrain terrain, Vec3 normal) {
    var sum = Vec3.ZERO;
    int count = 0;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (anchored(leg)
          && foot != null
          && foot.planted != null
          && terrain.normal(foot.planted).dot(normal) > SAME_SURFACE_NORMAL_DOT) {
        var point = terrain.position(foot.planted);
        if (point != null) {
          sum = sum.add(point);
          count++;
        }
      }
    }
    return count > 0 ? sum.scale(1d / count) : null;
  }

  public double anchorHeightError(List<Leg> legs, Vec3 normal) {
    double sum = 0;
    int count = 0;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (anchored(leg) && foot != null && foot.planted != null) {
        sum += leg.height + foot.point.subtract(leg.hip).dot(normal);
        count++;
      }
    }
    return count == 0 ? Double.NaN : sum / count;
  }

  public List<org.azkerash.createbioships.core.locomotion.LegTurnDrive.Foot> turnSupports(
      List<Leg> legs) {
    var result = new ArrayList<org.azkerash.createbioships.core.locomotion.LegTurnDrive.Foot>();
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (foot != null && foot.planted != null) {
        result.add(
            new org.azkerash.createbioships.core.locomotion.LegTurnDrive.Foot(
                foot.point, foot.planted.support));
      }
    }
    return List.copyOf(result);
  }

  public int contacts() {
    return (int) feet.values().stream().filter(f -> f.planted != null).count();
  }

  public double supportHeight(List<Leg> legs, double rootY) {
    return supportHeight(legs, rootY, contact -> true);
  }

  public double groundSupportHeight(
      List<Leg> legs, double rootY, Terrain terrain, double minimumUp) {
    return supportHeight(legs, rootY, contact -> terrain.normal(contact).y >= minimumUp);
  }

  private double supportHeight(
      List<Leg> legs, double rootY, java.util.function.Predicate<Contact> eligible) {
    double sum = 0;
    int count = 0;
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if (foot != null && foot.planted != null) {
        if (!eligible.test(foot.planted)) {
          continue;
        }
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
    if (legs.stream().anyMatch(PrototypeGait::anchored)) {
      // Three is the minimum non-collinear support polygon, not a sufficient
      // support condition. supportsBody separately checks actual placement.
      return ArachnidBodyMotor.MIN_ANCHORS;
    }
    return Math.abs(surface.grade()) > PrototypeStance.ADAPTATION_GRADE
            && legs.stream().anyMatch(PrototypeStance::adapts)
        ? Math.max(Math.min(3, legs.size()), legs.size() / 2)
        : Math.max(2, legs.size() / 2);
  }

  private List<Vec3> plantedPositions(List<Leg> legs, Terrain terrain, Leg excluded) {
    var contacts = new ArrayList<Vec3>();
    for (var leg : legs) {
      var foot = feet.get(leg.socket);
      if ((excluded == null || !leg.socket.equals(excluded.socket))
          && foot != null
          && foot.planted != null) {
        var contact = terrain.position(foot.planted);
        if (contact != null) {
          contacts.add(contact);
        }
      }
    }
    return contacts;
  }

  public boolean supportsBody(List<Leg> legs, Terrain terrain, Vec3 massCenter) {
    var normal =
        legs.stream().anyMatch(PrototypeGait::anchored)
            ? anchorNormal(legs, terrain)
            : new Vec3(0, 1, 0);
    return org.azkerash.createbioships.core.locomotion.SupportPolygon.containsProjection(
        massCenter, plantedPositions(legs, terrain, null), normal);
  }

  /** Wait for the swinging foot when advancing would move the load outside the planted feet. */
  public Vec3 supportedMotion(List<Leg> legs, Terrain terrain, Vec3 requested) {
    if (weightShift != null) {
      // Transfer only as far as the next support boundary, rather than swaying
      // all the way to the other feet's centroid on every step.
      return weightShift
          .normalize()
          .scale(Math.min(WEIGHT_SHIFT_SPEED, weightShift.length() * WEIGHT_SHIFT_GAIN));
    }
    var massCenter = terrain.massCenter();
    boolean swinging = feet.values().stream().anyMatch(foot -> foot.destination != null);
    if (massCenter != null
        && swinging
        && !supportsBody(
            legs, terrain, massCenter.add(requested.scale(SUPPORT_LOOKAHEAD_SECONDS)))) {
      return Vec3.ZERO;
    }
    return requested;
  }

  public long getProbeCount() {
    return probes;
  }

  public long getStepCount() {
    return steps;
  }
}
