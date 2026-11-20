package org.azkerash.createbioships.core.pathfinding;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.SubLevelCreatureParts;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.locomotion.SubLevelFacing;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;

/** One transient follower per behavior: the primary route survives temporary obstacle detours. */
public final class WaypointNavigator {
  public record Result(
      int next,
      boolean arrived,
      boolean accepted,
      boolean detouring,
      double headingError,
      double remaining,
      long revision) {}

  /**
   * Narrow movement/planning boundary also permits deterministic obstacle tests without physics.
   */
  public interface Environment {
    Vec3 position();

    Vec3 forward();

    long tick();

    boolean canStrafe();

    default double detourDistance() {
      return 2;
    }

    boolean move(Vec3 velocity, double yawRate);

    List<Vec3> plan(Vec3 destination, SubLevelPathfinder.Mode mode);

    void publish(List<Vec3> points, int next, SubLevelPathfinder.Mode mode);
  }

  private static final double ARRIVAL_RADIUS = .55;
  private static final double ARRIVAL_LOOKAHEAD_SECONDS = .15;
  private static final double STRAFE_SPEED_FACTOR = .65;
  private static final int REPLAN_INTERVAL_TICKS = 20;
  private static final int DETOUR_LIFETIME_TICKS = 400;
  private static final int MAX_LOCAL_DETOURS = 2;
  private static final double SUPPORT_PROBE_LIFT = 2, SUPPORT_PROBE_DEPTH = 12;
  private static final double SUPPORT_TOLERANCE = .05;
  private static final double SUPPORT_SAMPLE_SPACING = 1;
  private static final int MAX_SUPPORT_SAMPLES = 4;
  private static final double MOVEMENT_LOOKAHEAD_SECONDS = .5;
  private List<Vec3> primary = List.of(), detour = List.of();
  private SubLevelPathfinder.Mode mode;
  private int detourNext, rejoin, localDetours;
  private boolean localDetour;
  private double terrainFactor = 1;
  private long terrainTick = Long.MIN_VALUE;

  private record Support(boolean supported, double factor) {}

  private static final Support BLOCKED_SUPPORT = new Support(false, 0);
  private static final Support LEVEL_SUPPORT = new Support(true, 1);
  private long nextPlan = Long.MIN_VALUE, detourUntil, revision;

  public void reset() {
    primary = detour = List.of();
    detourNext = 0;
    localDetours = 0;
    localDetour = false;
    nextPlan = Long.MIN_VALUE;
    mode = null;
    terrainFactor = 1;
    terrainTick = Long.MIN_VALUE;
    revision++;
  }

  public List<Vec3> getDetourWaypoints() {
    return detour;
  }

  public Result follow(
      ServerSubLevel subject,
      List<Vec3> route,
      int next,
      double speed,
      int leaseTicks,
      SubLevelPathfinder.Mode kind) {
    var body = SubLevelCreatureParts.root(subject);
    return follow(
        new Environment() {
          public Vec3 position() {
            return SubLevelPathfinder.position(body);
          }

          public Vec3 forward() {
            return SubLevelFacing.world(body);
          }

          public long tick() {
            return body.getLevel().getGameTime();
          }

          public boolean canStrafe() {
            // Legs can sidestep while walking, including underwater. Fin propulsion cannot.
            return kind == SubLevelPathfinder.Mode.GROUND && SubLevelLocomotion.get(body).hasLegs();
          }

          public double detourDistance() {
            var bounds = SubLevelLocomotion.bounds(body);
            return Math.clamp(Math.max(bounds.getXsize(), bounds.getZsize()) / 2, 1, 4);
          }

          public boolean move(Vec3 velocity, double yawRate) {
            var support = support(body, velocity, kind);
            if (!support.supported) {
              BrainMotorControls.stop(body);
              return false;
            }
            long now = tick();
            double seconds =
                terrainTick == Long.MIN_VALUE ? 0 : Math.max(0, now - terrainTick) / 20d;
            double factor =
                kind == SubLevelPathfinder.Mode.WATER
                    ? 1
                    : TerrainSpeedControl.recover(terrainFactor, support.factor, seconds);
            boolean accepted =
                BrainMotorControls.move(body, velocity.scale(factor), yawRate, leaseTicks, kind);
            if (accepted && kind == SubLevelPathfinder.Mode.GROUND && velocity.lengthSqr() > 1e-8) {
              terrainFactor = factor;
              terrainTick = now;
            }
            return accepted;
          }

          public List<Vec3> plan(Vec3 destination, SubLevelPathfinder.Mode selected) {
            return SubLevelPathfinder.find(body, destination, selected).points();
          }

          public void publish(List<Vec3> points, int index, SubLevelPathfinder.Mode selected) {
            SubLevelPaths.update(body, selected, points, index);
          }
        },
        route,
        next,
        speed,
        kind);
  }

  public Result follow(
      Environment world, List<Vec3> route, int next, double speed, SubLevelPathfinder.Mode kind) {
    if (!primary.equals(route) || mode != kind) {
      reset();
      primary = List.copyOf(route);
      mode = kind;
    }
    double radius = Math.max(ARRIVAL_RADIUS, speed * ARRIVAL_LOOKAHEAD_SECONDS);
    var position = world.position();
    next = Math.clamp(next, 0, primary.size());
    if (!detour.isEmpty() && (world.tick() >= detourUntil || next > rejoin)) {
      clearDetour();
    }
    if (!detour.isEmpty()) {
      while (detourNext < detour.size() && distance(position, detour.get(detourNext)) < radius) {
        detourNext++;
      }
      if (detourNext >= detour.size()) {
        if (!localDetour) {
          next = rejoin + 1;
        }
        clearDetour();
      }
    }
    if (detour.isEmpty()) {
      while (next < primary.size() && distance(position, primary.get(next)) < radius) {
        next++;
      }
    }
    if (next >= primary.size()) {
      world.move(Vec3.ZERO, 0);
      world.publish(List.of(), 0, kind);
      return new Result(next, true, true, false, 0, 0, revision);
    }
    var choice = steering(world, position, next, speed);
    boolean accepted = world.move(choice.velocity(), choice.yawRate());
    if (!accepted && world.tick() >= nextPlan) {
      nextPlan = world.tick() + REPLAN_INTERVAL_TICKS;
      // One bounded search per retry; no per-leg or per-frame obstacle scans.
      int candidate = next;
      for (int i = primary.size() - 1; i >= next; i--) {
        if (position.distanceTo(primary.get(i)) <= SubLevelPathfinder.MAX_ROUTE_DISTANCE_BLOCKS) {
          candidate = i;
          break;
        }
      }
      var planned = world.plan(primary.get(candidate), kind);
      if (!planned.isEmpty()) {
        var replacement = List.copyOf(planned);
        if (!replacement.equals(detour)) {
          revision++;
        }
        detour = replacement;
        localDetour = false;
        rejoin = candidate;
        detourNext = 0;
        detourUntil = world.tick() + DETOUR_LIFETIME_TICKS;
        while (detourNext < detour.size() - 1
            && distance(position, detour.get(detourNext)) < radius) {
          detourNext++;
        }
        choice = steering(world, position, next, speed);
        accepted = world.move(choice.velocity(), choice.yawRate());
      }
      if (!accepted && world.canStrafe() && localDetours < MAX_LOCAL_DETOURS) {
        var nose = world.forward();
        var right = new Vec3(nose.z, 0, -nose.x).normalize();
        var facing =
            SubLevelFacing.towards(nose, primary.get(candidate).subtract(position), speed, false);
        // Contact may leave no valid first A* cell. Try a bounded, live-checked sidestep waypoint.
        for (int side : new int[] {1, -1}) {
          var direction = right.scale(side);
          var velocity = direction.scale(speed * STRAFE_SPEED_FACTOR);
          if (world.move(velocity, facing.yawRate())) {
            detour = List.of(position.add(direction.scale(world.detourDistance())));
            detourNext = 0;
            localDetour = true;
            localDetours++;
            rejoin = candidate;
            detourUntil = world.tick() + DETOUR_LIFETIME_TICKS;
            revision++;
            choice = new SubLevelFacing.Steering(velocity, facing.yawRate(), facing.error());
            accepted = true;
            break;
          }
        }
      }
    }
    if (detour.isEmpty()) {
      world.publish(primary, next, kind);
    } else {
      var active = new ArrayList<>(detour);
      active.addAll(primary.subList(localDetour ? next : rejoin + 1, primary.size()));
      world.publish(List.copyOf(active), detourNext, kind);
    }
    double remaining =
        detour.isEmpty()
            ? remaining(position, primary, next)
            : remaining(position, detour, detourNext)
                + (localDetour
                    ? remaining(detour.getLast(), primary, next)
                    : remaining(primary.get(rejoin), primary, rejoin + 1));
    return new Result(
        next, false, accepted, !detour.isEmpty(), choice.error(), remaining, revision);
  }

  private SubLevelFacing.Steering steering(
      Environment world, Vec3 position, int next, double speed) {
    if (detour.isEmpty()) {
      return SubLevelFacing.towards(
          world.forward(),
          primary.get(next).subtract(position),
          speed,
          mode == SubLevelPathfinder.Mode.WATER);
    }
    var translation = detour.get(detourNext).subtract(position);
    if (!world.canStrafe()) {
      return SubLevelFacing.towards(
          world.forward(), translation, speed, mode == SubLevelPathfinder.Mode.WATER);
    }
    // A legged creature can translate toward its detour while its nose tracks the rejoin target.
    var facing =
        SubLevelFacing.towards(
            world.forward(), primary.get(rejoin).subtract(position), speed, false);
    var lateral =
        new Vec3(translation.x, 0, translation.z).normalize().scale(speed * STRAFE_SPEED_FACTOR);
    return new SubLevelFacing.Steering(lateral, facing.yawRate(), facing.error());
  }

  private void clearDetour() {
    localDetour = false;
    detour = List.of();
    detourNext = 0;
    revision++;
  }

  private double distance(Vec3 a, Vec3 b) {
    return mode == SubLevelPathfinder.Mode.WATER
        ? a.distanceTo(b)
        : Math.hypot(a.x - b.x, a.z - b.z);
  }

  private double remaining(Vec3 position, List<Vec3> points, int next) {
    if (next >= points.size()) {
      return 0;
    }
    double length = distance(position, points.get(next));
    for (int i = next + 1; i < points.size(); i++) {
      length += distance(points.get(i - 1), points.get(i));
    }
    return length;
  }

  private static Support support(ServerSubLevel body, Vec3 velocity, SubLevelPathfinder.Mode kind) {
    if (velocity.lengthSqr() < 1e-8) {
      return LEVEL_SUPPORT;
    }
    var here = SubLevelPathfinder.position(body);
    var target = here.add(velocity.scale(MOVEMENT_LOOKAHEAD_SECONDS));
    if (!body.getLevel().hasChunkAt(BlockPos.containing(target))) {
      return BLOCKED_SUPPORT;
    }
    if (kind == SubLevelPathfinder.Mode.WATER) {
      return body.getLevel().getFluidState(BlockPos.containing(target)).is(FluidTags.WATER)
          ? LEVEL_SUPPORT
          : BLOCKED_SUPPORT;
    }
    var family = new HashSet<UUID>();
    SubLevelCreatureParts.members(body).forEach(part -> family.add(part.getUniqueId()));
    Support[] result = {BLOCKED_SUPPORT};
    TerrainProbe.withIgnored(
        part -> family.contains(part.getUniqueId()),
        () -> {
          var delta = target.subtract(here);
          int samples =
              Math.clamp(
                  (int) Math.ceil(delta.horizontalDistance() / SUPPORT_SAMPLE_SPACING),
                  1,
                  MAX_SUPPORT_SAMPLES);
          Vec3 previous = null;
          double factor = 1;
          for (int i = 0; i <= samples; i++) {
            var sample = here.add(delta.scale(i / (double) samples));
            if (!body.getLevel().hasChunkAt(BlockPos.containing(sample))) {
              return;
            }
            var hit =
                TerrainProbe.down(
                    body.getLevel(),
                    sample.add(0, SUPPORT_PROBE_LIFT, 0),
                    SUPPORT_PROBE_DEPTH,
                    body);
            if (hit == null
                || !TerrainProbe.standable(hit)
                || (previous != null
                    && Math.abs(hit.point().y - previous.y)
                        > SubLevelLocomotion.stepHeight(body) + SUPPORT_TOLERANCE)) {
              return;
            }
            if (previous != null) {
              double run = Math.hypot(delta.x, delta.z) / samples;
              factor =
                  Math.min(factor, TerrainSpeedControl.factor(hit.point().y - previous.y, run));
            }
            previous = hit.point();
          }
          result[0] = new Support(true, factor);
        });
    return result[0];
  }
}
