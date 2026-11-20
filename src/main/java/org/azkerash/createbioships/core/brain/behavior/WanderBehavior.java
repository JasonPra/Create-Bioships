package org.azkerash.createbioships.core.brain.behavior;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.brain.BrainMotorControls;
import org.azkerash.createbioships.core.pathfinding.SubLevelPathfinder;

/**
 * Short local routes and deliberate pauses. All route selection is delegated to the shared planner.
 */
public final class WanderBehavior implements BrainBehavior {
  public static final ResourceLocation ID = ResourceLocation.parse("create_bioships:wander");
  // Durations use server ticks; distances use world blocks and heading errors use radians.
  private static final int INITIAL_PAUSE_TICKS = 40;
  private static final int ARRIVAL_PAUSE_TICKS = 30;
  private static final int ARRIVAL_PAUSE_JITTER_TICKS = 50;
  private static final int RETRY_PAUSE_TICKS = 60;
  private static final int PROGRESS_TIMEOUT_TICKS = 80;
  private static final int MOTOR_COMMAND_TICKS = 20;
  private static final int ROUTE_ATTEMPTS = 8;
  private static final int ESCAPE_TICKS = 20;
  private static final double ESCAPE_SPEED = .65;
  private static final double MIN_RADIUS_SCALE = .2;
  private static final double BLOCKED_DIRECTION_DOT = .5;
  private static final double ESCAPE_PROBE_LIFT_BLOCKS = 2;
  private static final double ESCAPE_PROBE_DEPTH_BLOCKS = 12;
  private static final double SUPPORT_HEIGHT_TOLERANCE_BLOCKS = .05;
  private static final double WAYPOINT_RADIUS_BLOCKS = .55;
  private static final double ARRIVAL_RADIUS_BLOCKS = .6;
  private static final double MIN_ROUTE_DISTANCE_BLOCKS = 4;
  private static final double ROUTE_DISTANCE_JITTER_BLOCKS = 5;
  private static final double WATER_VERTICAL_RADIUS_BLOCKS = 3;
  private static final double MIN_PROGRESS_BLOCKS = .2;
  private static final double MIN_HEADING_PROGRESS_RADIANS = .1;
  private static final double WANDER_SPEED = .65;
  private final org.azkerash.createbioships.core.pathfinding.WaypointNavigator navigator =
      new org.azkerash.createbioships.core.pathfinding.WaypointNavigator();
  private long navigationRevision = Long.MIN_VALUE;
  private List<Vec3> route = List.of();
  private Vec3 escapeVelocity, retryGoal, avoidedDirection;
  private long escapeUntil;
  private int waypoint;
  private long nextDecision = Long.MIN_VALUE, lastProgress;
  private double bestRemaining = Double.POSITIVE_INFINITY, bestHeading = Double.POSITIVE_INFINITY;
  private long motionRevision = Long.MIN_VALUE;
  private SubLevelPathfinder.Mode mode = SubLevelPathfinder.Mode.GROUND;

  @Override
  public void tick(ServerSubLevel body) {
    long now = body.getLevel().getGameTime();
    long revision =
        org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.motionRevision(body);
    if (revision != motionRevision) {
      motionRevision = revision;
      escapeVelocity = retryGoal = avoidedDirection = null;
      route = List.of();
      waypoint = 0;
      nextDecision = Long.MIN_VALUE;
    }

    var flags = org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.get(body);
    var location = SubLevelPathfinder.position(body);
    var selected =
        flags.hasFins()
                && body.getLevel()
                    .getFluidState(net.minecraft.core.BlockPos.containing(location))
                    .is(net.minecraft.tags.FluidTags.WATER)
            ? SubLevelPathfinder.Mode.WATER
            : SubLevelPathfinder.Mode.GROUND;
    if (selected != mode) {
      escapeVelocity = retryGoal = avoidedDirection = null;
      mode = selected;
      route = List.of();
      nextDecision = Long.MIN_VALUE;
    }
    if (!SubLevelPathfinder.allowed(body, mode)) {
      escapeVelocity = retryGoal = null;
      BrainMotorControls.stop(body);
      route = List.of();
      org.azkerash.createbioships.core.pathfinding.SubLevelPaths.clear(body);
      nextDecision = Long.MIN_VALUE;
      return;
    }
    if (escapeVelocity != null) {
      if (now < escapeUntil
          && BrainMotorControls.move(body, escapeVelocity, 0, MOTOR_COMMAND_TICKS, mode)) {
        org.azkerash.createbioships.core.pathfinding.SubLevelPaths.update(
            body, mode, List.of(location.add(escapeVelocity)), 0);
        return;
      }
      escapeVelocity = null;
      nextDecision = now;
      if (retryGoal != null && installRoute(body, location, retryGoal, now)) {
        retryGoal = null;
      }
    }
    if (nextDecision == Long.MIN_VALUE) {
      pause(body, now + INITIAL_PAUSE_TICKS);
      return;
    }
    if (now < nextDecision) {
      BrainMotorControls.move(body, Vec3.ZERO, 0, MOTOR_COMMAND_TICKS, mode);
      return;
    }
    var position = SubLevelPathfinder.position(body);
    while (navigator.getDetourWaypoints().isEmpty()
        && waypoint < route.size()
        && distance(position, route.get(waypoint)) < WAYPOINT_RADIUS_BLOCKS) {
      waypoint++;
      bestHeading = Double.POSITIVE_INFINITY;
    }
    if (!route.isEmpty() && waypoint >= route.size()) {
      pause(
          body,
          now + ARRIVAL_PAUSE_TICKS + body.getLevel().random.nextInt(ARRIVAL_PAUSE_JITTER_TICKS));
      return;
    }
    if (waypoint >= route.size()) {
      if (!chooseRoute(body, position, now)) {
        recover(body, position, now);
        return;
      }
    }

    var progress = navigator.follow(body, route, waypoint, WANDER_SPEED, MOTOR_COMMAND_TICKS, mode);
    waypoint = progress.next();
    if (progress.arrived()) {
      pause(
          body,
          now + ARRIVAL_PAUSE_TICKS + body.getLevel().random.nextInt(ARRIVAL_PAUSE_JITTER_TICKS));
      return;
    }
    double heading = Math.abs(progress.headingError());
    if (navigationRevision != progress.revision()) {
      navigationRevision = progress.revision();
      // A detour can be longer. Change the distance reference without inventing physical progress.
      bestRemaining = progress.remaining();
      bestHeading = heading;
    }
    if (progress.remaining() < bestRemaining - MIN_PROGRESS_BLOCKS) {
      bestRemaining = progress.remaining();
      lastProgress = now;
    }
    if (heading < bestHeading - MIN_HEADING_PROGRESS_RADIANS) {
      bestHeading = heading;
      lastProgress = now;
    }
    // The navigator gets bounded retries before the behavior abandons the main destination.
    if (now - lastProgress > PROGRESS_TIMEOUT_TICKS) {
      recover(body, position, now);
      return;
    }
    if (waypoint == route.size() - 1
        && distance(position, route.getLast()) < ARRIVAL_RADIUS_BLOCKS) {
      pause(
          body,
          now + ARRIVAL_PAUSE_TICKS + body.getLevel().random.nextInt(ARRIVAL_PAUSE_JITTER_TICKS));
    }
  }

  private boolean installRoute(ServerSubLevel body, Vec3 position, Vec3 goal, long now) {
    var candidate = SubLevelPathfinder.find(body, goal, mode);
    if (!candidate.found()) {
      return false;
    }
    int first = 0;
    while (first < candidate.points().size()
        && distance(position, candidate.points().get(first)) < WAYPOINT_RADIUS_BLOCKS) {
      first++;
    }
    if (first >= candidate.points().size()) {
      return false;
    }
    var steering =
        org.azkerash.createbioships.core.locomotion.SubLevelFacing.towards(
            org.azkerash.createbioships.core.locomotion.SubLevelFacing.world(body),
            candidate.points().get(first).subtract(position),
            WANDER_SPEED,
            mode == SubLevelPathfinder.Mode.WATER);
    if (!SubLevelPathfinder.movementClear(body, steering.velocity(), steering.yawRate())) {
      return false;
    }
    navigator.reset();
    route = candidate.points();
    waypoint = first;
    nextDecision = now;
    bestRemaining = bestHeading = Double.POSITIVE_INFINITY;
    lastProgress = now;
    save(body, mode == SubLevelPathfinder.Mode.WATER ? "swimming" : "walking", now);
    return true;
  }

  private boolean chooseRoute(ServerSubLevel body, Vec3 position, long now) {
    for (int attempt = 0; attempt < ROUTE_ATTEMPTS; attempt++) {
      double angle = body.getLevel().random.nextDouble() * Math.PI * 2;
      // Try shorter destinations too: an obstructed area may have no reachable goal 4–9 blocks
      // away.
      double scale = 1 - (1 - MIN_RADIUS_SCALE) * attempt / (ROUTE_ATTEMPTS - 1d);
      double radius =
          (MIN_ROUTE_DISTANCE_BLOCKS
                  + body.getLevel().random.nextDouble() * ROUTE_DISTANCE_JITTER_BLOCKS)
              * scale;
      var delta =
          new Vec3(
              Math.cos(angle) * radius,
              mode == SubLevelPathfinder.Mode.WATER
                  ? (body.getLevel().random.nextDouble() * WATER_VERTICAL_RADIUS_BLOCKS * 2
                          - WATER_VERTICAL_RADIUS_BLOCKS)
                      * scale
                  : 0,
              Math.sin(angle) * radius);
      if (avoidedDirection != null
          && delta.normalize().dot(avoidedDirection) > BLOCKED_DIRECTION_DOT) {
        continue;
      }
      if (installRoute(body, position, position.add(delta), now)) {
        avoidedDirection = null;
        return true;
      }
    }
    return false;
  }

  /**
   * Replan first; if contact leaves no turning clearance, take a bounded collision-checked escape.
   */
  private void recover(ServerSubLevel body, Vec3 position, long now) {
    BrainMotorControls.stop(body);
    var goal = route.isEmpty() ? retryGoal : route.getLast();
    if (goal != null) {
      avoidedDirection = goal.subtract(position).normalize();
      if (installRoute(body, position, goal, now)) {
        return;
      }
    }
    route = List.of();
    waypoint = 0;
    org.azkerash.createbioships.core.pathfinding.SubLevelPaths.clear(body);
    if (chooseRoute(body, position, now)) {
      return;
    }
    var forward = org.azkerash.createbioships.core.locomotion.SubLevelFacing.world(body);
    var horizontal = new Vec3(forward.x, 0, forward.z).normalize();
    // No obstacle type or world coordinates are assumed. Reverse/side steps create room to turn.
    var directions = new ArrayList<Vec3>();
    if (mode == SubLevelPathfinder.Mode.GROUND) {
      for (int step : new int[] {4, 3, 5, 2, 6, 1, 7, 0}) {
        directions.add(horizontal.yRot((float) (step * Math.PI / 4)).normalize());
      }
    } else {
      // Flippers create clearance with forward motion or a climb/dive, never a sideways slide.
      directions.add(horizontal);
    }
    if (mode == SubLevelPathfinder.Mode.WATER) {
      directions.add(new Vec3(0, 1, 0));
      directions.add(new Vec3(0, -1, 0));
    }
    for (var direction : directions) {
      var velocity = direction.scale(ESCAPE_SPEED);
      if (!escapeSupported(body, position, direction)) {
        continue;
      }
      if (BrainMotorControls.move(body, velocity, 0, MOTOR_COMMAND_TICKS, mode)) {
        escapeVelocity = velocity;
        retryGoal = goal;
        escapeUntil = now + ESCAPE_TICKS;
        save(body, "recovering", escapeUntil);
        return;
      }
    }
    retryGoal = null;
    pause(body, now + RETRY_PAUSE_TICKS);
  }

  private boolean escapeSupported(ServerSubLevel body, Vec3 position, Vec3 direction) {
    var destination = position.add(direction);
    if (mode == SubLevelPathfinder.Mode.WATER) {
      return body.getLevel()
          .getFluidState(net.minecraft.core.BlockPos.containing(destination))
          .is(net.minecraft.tags.FluidTags.WATER);
    }
    var current =
        dev.letitwalk.walker.TerrainProbe.down(
            body.getLevel(),
            position.add(0, ESCAPE_PROBE_LIFT_BLOCKS, 0),
            ESCAPE_PROBE_DEPTH_BLOCKS,
            body);
    var target =
        dev.letitwalk.walker.TerrainProbe.down(
            body.getLevel(),
            destination.add(0, ESCAPE_PROBE_LIFT_BLOCKS, 0),
            ESCAPE_PROBE_DEPTH_BLOCKS,
            body);
    return current != null
        && target != null
        && dev.letitwalk.walker.TerrainProbe.standable(target)
        && Math.abs(target.point().y - current.point().y)
            <= org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.stepHeight(body)
                + .05;
  }

  private void pause(ServerSubLevel body, long until) {
    navigator.reset();
    org.azkerash.createbioships.core.pathfinding.SubLevelPaths.clear(body);
    escapeVelocity = retryGoal = null;
    nextDecision = until;
    route = List.of();
    waypoint = 0;
    bestRemaining = Double.POSITIVE_INFINITY;
    BrainMotorControls.move(body, Vec3.ZERO, 0, MOTOR_COMMAND_TICKS, mode);
    save(body, "paused", until);
  }

  private double distance(Vec3 a, Vec3 b) {
    return mode == SubLevelPathfinder.Mode.WATER
        ? a.distanceTo(b)
        : Math.hypot(a.x - b.x, a.z - b.z);
  }

  private void save(ServerSubLevel body, String phase, long until) {
    var root = body.getUserDataTag().copy();
    var memory = new CompoundTag();
    memory.putString("program", ID.toString());
    memory.putString("phase", phase);
    memory.putString("mode", mode.name());
    memory.putLong("nextDecision", until);
    root.put(SubLevelData.BEHAVIOR, memory);
    body.setUserDataTag(root);
  }

  @Override
  public void stop(ServerSubLevel body) {
    navigator.reset();
    BrainBehavior.super.stop(body);
    if (!body.isRemoved()) {
      save(body, "stopped", 0);
    }
  }
}
