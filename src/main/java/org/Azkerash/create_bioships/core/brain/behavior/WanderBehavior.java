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
  private List<Vec3> route = List.of();
  private int waypoint;
  private long nextDecision = Long.MIN_VALUE, lastProgress;
  private double bestRemaining = Double.POSITIVE_INFINITY;
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
      pause(body, now + 40);
      return;
    }
    if (now < nextDecision) {
      BrainMotorControls.move(body, Vec3.ZERO, 0, 20, mode);
      return;
    }
    var position = SubLevelPathfinder.position(body);
    while (waypoint < route.size() && distance(position, route.get(waypoint)) < .55) waypoint++;
    if (!route.isEmpty() && waypoint >= route.size()) {
      pause(body, now + 30 + body.getLevel().random.nextInt(50));
      return;
    }
    if (waypoint >= route.size()) {
      route = List.of();
      waypoint = 0;
      for (int attempt = 0; attempt < 4; attempt++) {
        double angle = body.getLevel().random.nextDouble() * Math.PI * 2;
        double distance = 4 + body.getLevel().random.nextDouble() * 5;
        var goal =
            position.add(
                Math.cos(angle) * distance,
                mode == SubLevelPathfinder.Mode.WATER
                    ? body.getLevel().random.nextDouble() * 6 - 3
                    : 0,
                Math.sin(angle) * distance);
        var candidate = SubLevelPathfinder.find(body, goal, mode);
        if (candidate.found()) {
          route = candidate.points();
          break;
        }
      }
      if (route.isEmpty()) {
        pause(body, now + 60);
        return;
      }
      bestRemaining = Double.POSITIVE_INFINITY;
      lastProgress = now;
      save(body, mode == SubLevelPathfinder.Mode.WATER ? "swimming" : "walking", now);
    }
    double remaining = distance(position, route.get(waypoint));
    for (int i = waypoint + 1; i < route.size(); i++)
      remaining += distance(route.get(i - 1), route.get(i));
    // Rocking or moving sideways against a wall is not progress along the route.
    if (remaining < bestRemaining - .2) {
      bestRemaining = remaining;
      lastProgress = now;
    }
    if (now - lastProgress > 80) {
      pause(body, now + 40);
      return;
    }
    org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.update(body, mode, route, waypoint);
    var delta = route.get(waypoint).subtract(position);
    var direction =
        (mode == SubLevelPathfinder.Mode.WATER ? delta : new Vec3(delta.x, 0, delta.z)).normalize();
    var facing = body.logicalPose().orientation().transform(new org.joml.Vector3d(0, 0, 1));
    double angle = Math.atan2(direction.x, direction.z) - Math.atan2(facing.x, facing.z);
    angle = Math.atan2(Math.sin(angle), Math.cos(angle));
    if (!BrainMotorControls.move(
        body, direction.scale(.65), Math.clamp(angle, -.6, .6), 20, mode)) {
      pause(body, now + 60);
      return;
    }
    if (waypoint == route.size() - 1 && distance(position, route.getLast()) < .6)
      pause(body, now + 30 + body.getLevel().random.nextInt(50));
  }

  private void pause(ServerSubLevel body, long until) {
    org.Azkerash.create_bioships.core.pathfinding.SubLevelPaths.clear(body);
    nextDecision = until;
    route = List.of();
    waypoint = 0;
    bestRemaining = Double.POSITIVE_INFINITY;
    BrainMotorControls.move(body, Vec3.ZERO, 0, 20, mode);
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
    if (!body.isRemoved()) save(body, "stopped", 0);
  }
}
