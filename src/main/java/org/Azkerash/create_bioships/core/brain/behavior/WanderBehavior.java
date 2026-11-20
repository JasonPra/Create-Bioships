package org.Azkerash.create_bioships.core.brain.behavior;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.BrainMotorControls;
import org.Azkerash.create_bioships.core.pathfinding.SubLevelPathfinder;

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
  private static final int ROUTE_ATTEMPTS = 4;
  private static final double WAYPOINT_RADIUS_BLOCKS = .55;
  private static final double ARRIVAL_RADIUS_BLOCKS = .6;
  private static final double MIN_ROUTE_DISTANCE_BLOCKS = 4;
  private static final double ROUTE_DISTANCE_JITTER_BLOCKS = 5;
  private static final double WATER_VERTICAL_RADIUS_BLOCKS = 3;
  private static final double MIN_PROGRESS_BLOCKS = .2;
  private static final double MIN_HEADING_PROGRESS_RADIANS = .1;
  private static final double WANDER_SPEED = .65;
  private List<Vec3> route = List.of();
  private int waypoint;
  private long nextDecision = Long.MIN_VALUE, lastProgress;
  private double bestRemaining = Double.POSITIVE_INFINITY, bestHeading = Double.POSITIVE_INFINITY;
  private long motionRevision = Long.MIN_VALUE;
  private SubLevelPathfinder.Mode mode = SubLevelPathfinder.Mode.GROUND;

  @Override
  public void tick(ServerSubLevel body) {
    long now = body.getLevel().getGameTime();
    long revision =
        org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.motionRevision(body);
    if (revision != motionRevision) {
      motionRevision = revision;
      route = List.of();
      waypoint = 0;
      nextDecision = Long.MIN_VALUE;
    }

    var flags = org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion.get(body);
    var location = SubLevelPathfinder.position(body);
    var selected =
        flags.hasFins()
                && body.getLevel()
                    .getFluidState(net.minecraft.core.BlockPos.containing(location))
                    .is(net.minecraft.tags.FluidTags.WATER)
            ? SubLevelPathfinder.Mode.WATER
            : SubLevelPathfinder.Mode.GROUND;
    if (selected != mode) {
      mode = selected;
      route = List.of();
      nextDecision = Long.MIN_VALUE;
    }
    if (!SubLevelPathfinder.allowed(body, mode)) {
      BrainMotorControls.stop(body);
      route = List.of();
      org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.clear(body);
      nextDecision = Long.MIN_VALUE;
      return;
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
    while (waypoint < route.size()
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
      route = List.of();
      waypoint = 0;
      for (int attempt = 0; attempt < ROUTE_ATTEMPTS; attempt++) {
        double angle = body.getLevel().random.nextDouble() * Math.PI * 2;
        double distance =
            MIN_ROUTE_DISTANCE_BLOCKS
                + body.getLevel().random.nextDouble() * ROUTE_DISTANCE_JITTER_BLOCKS;
        var goal =
            position.add(
                Math.cos(angle) * distance,
                mode == SubLevelPathfinder.Mode.WATER
                    ? body.getLevel().random.nextDouble() * (WATER_VERTICAL_RADIUS_BLOCKS * 2)
                        - WATER_VERTICAL_RADIUS_BLOCKS
                    : 0,
                Math.sin(angle) * distance);
        var candidate = SubLevelPathfinder.find(body, goal, mode);
        if (candidate.found()) {
          route = candidate.points();
          break;
        }
      }
      if (route.isEmpty()) {
        pause(body, now + RETRY_PAUSE_TICKS);
        return;
      }
      bestRemaining = Double.POSITIVE_INFINITY;
      bestHeading = Double.POSITIVE_INFINITY;
      lastProgress = now;
      save(body, mode == SubLevelPathfinder.Mode.WATER ? "swimming" : "walking", now);
    }
    double remaining = distance(position, route.get(waypoint));
    for (int i = waypoint + 1; i < route.size(); i++) {
      remaining += distance(route.get(i - 1), route.get(i));
    }
    // Rocking or moving sideways against a wall is not progress along the route.
    if (remaining < bestRemaining - MIN_PROGRESS_BLOCKS) {
      bestRemaining = remaining;
      lastProgress = now;
    }
    var delta = route.get(waypoint).subtract(position);
    var steering =
        org.Azkerash.create_bioships.core.locomotion.SubLevelFacing.towards(
            org.Azkerash.create_bioships.core.locomotion.SubLevelFacing.world(body),
            delta,
            WANDER_SPEED,
            mode == SubLevelPathfinder.Mode.WATER);
    // A deliberate turn towards the target is progress; rocking against terrain is not.
    double heading = Math.abs(steering.error());
    if (heading < bestHeading - MIN_HEADING_PROGRESS_RADIANS) {
      bestHeading = heading;
      lastProgress = now;
    }
    if (now - lastProgress > PROGRESS_TIMEOUT_TICKS) {
      pause(body, now + INITIAL_PAUSE_TICKS);
      return;
    }
    org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.update(body, mode, route, waypoint);
    if (!BrainMotorControls.move(
        body, steering.velocity(), steering.yawRate(), MOTOR_COMMAND_TICKS, mode)) {
      pause(body, now + RETRY_PAUSE_TICKS);
      return;
    }
    if (waypoint == route.size() - 1
        && distance(position, route.getLast()) < ARRIVAL_RADIUS_BLOCKS) {
      pause(
          body,
          now + ARRIVAL_PAUSE_TICKS + body.getLevel().random.nextInt(ARRIVAL_PAUSE_JITTER_TICKS));
    }
  }

  private void pause(ServerSubLevel body, long until) {
    org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.clear(body);
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
    BrainBehavior.super.stop(body);
    if (!body.isRemoved()) {
      save(body, "stopped", 0);
    }
  }
}
