package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Bounded ground navigation checks the whole current body, not just the center of its core. */
final class GroundRoute {
  private List<Vec3> path = List.of();
  private Vec3 destination;
  private int cursor;
  private long retryAfter;
  private boolean failed;
  private boolean complete;
  private String debugStatus = "idle";
  private long plannedAt;

  net.minecraft.nbt.CompoundTag debug(long now) {
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putString("Status", debugStatus);
    tag.putBoolean("Failed", failed);
    tag.putBoolean("Complete", complete);
    tag.putLong("Age", Math.max(0, now - plannedAt));
    tag.putLong("RetryIn", Math.max(0, retryAfter - now));
    if (destination != null) tag.put("Destination", VisionDebug.vector(destination));
    return tag;
  }

  void clear() {
    path = List.of();
    destination = null;
    cursor = 0;
    retryAfter = 0;
    failed = false;
    complete = false;
    debugStatus = "idle";
    plannedAt = 0;
  }

  boolean complete() {
    return complete;
  }

  boolean failed() {
    return failed;
  }

  List<Vec3> remaining() {
    return List.copyOf(path.subList(Math.min(cursor, path.size()), path.size()));
  }

  List<Vec3> points() {
    return List.copyOf(path);
  }

  record Grip(Direction direction, double distance) {}

  static Grip grip(LivingCoreBlockEntity core, Vec3 point) {
    if (!core.hatchling()) return null;
    var level = (ServerLevel) core.getLevel();
    Vec3 anchor =
        point.add(
            core.ship()
                .logicalPose()
                .transformNormal(
                    Vec3.atLowerCornerOf(core.structuralOrigin().subtract(core.getBlockPos()))));
    for (var face : Direction.Plane.HORIZONTAL)
      for (double height : new double[] {0, -.4, .4}) {
        var pos =
            BlockPos.containing(anchor.add(face.getStepX() * 1.2, height, face.getStepZ() * 1.2));
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !level.getFluidState(pos).isEmpty()
            || !level.getBlockState(pos).isFaceSturdy(level, pos, face.getOpposite())) continue;
        double boundary =
            face.getAxis() == Direction.Axis.X
                ? (face.getStepX() > 0 ? pos.getX() : pos.getX() + 1)
                : (face.getStepZ() > 0 ? pos.getZ() : pos.getZ() + 1);
        double distance =
            (boundary - anchor.get(face.getAxis())) * face.getAxisDirection().getStep();
        if (distance > .35 && distance <= 1.7) return new Grip(face, distance);
      }
    return null;
  }

  private List<Vec3> offsets(LivingCoreBlockEntity core) {
    return BioshipDecay.blocks(core.ship()).stream()
        .map(
            p ->
                core.ship()
                    .logicalPose()
                    .transformNormal(Vec3.atLowerCornerOf(p.subtract(core.getBlockPos()))))
        .toList();
  }

  private boolean fits(LivingCoreBlockEntity core, Vec3 point, List<Vec3> offsets) {
    return fits(core, point, offsets, .499);
  }

  private boolean fits(
      LivingCoreBlockEntity core, Vec3 point, List<Vec3> offsets, double clearance) {
    var level = (ServerLevel) core.getLevel();
    for (var offset : offsets) {
      Vec3 center = point.add(offset);
      for (var cell :
          BlockPos.betweenClosed(
              BlockPos.containing(center.add(-clearance, -.499, -clearance)),
              BlockPos.containing(center.add(clearance, .499, clearance))))
        if (!level.getWorldBorder().isWithinBounds(cell)
            || !level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4)
            || !BioshipIntegration.growthSpace(level, cell)
            || !level.getFluidState(cell).isEmpty()) return false;
    }
    return true;
  }

  private Vec3 standing(
      LivingCoreBlockEntity core, int x, int z, int floorTop, List<Vec3> offsets) {
    var level = (ServerLevel) core.getLevel();
    for (int dy : new int[] {0, 1, -1}) {
      var floor = new BlockPos(x, floorTop + dy - 1, z);
      if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)
          || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
          || !level.getFluidState(floor).isEmpty()) continue;
      Vec3 point =
          new Vec3(
              x + .5,
              floor.getY() + 1 + (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset(),
              z + .5);
      if (fits(core, point, offsets)) return point;
      // A leading/side organ meets a step before the core stands above that step. Legs can
      // brace from the lower floor and lift the entire body first, without creating flight.
      for (int lift = 1; lift <= core.crawl().extraLift(); lift++) {
        var raised = point.add(0, lift, 0);
        if (!core.canFly() && fits(core, raised, offsets)) return raised;
      }
    }
    var cling =
        new Vec3(
            x + .5, floorTop + (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset(), z + .5);
    return core.hatchling() && grip(core, cling) != null && fits(core, cling, offsets)
        ? cling
        : null;
  }

  private boolean firstStepClear(LivingCoreBlockEntity core, Vec3 from, Vec3 to, List<Vec3> parts) {
    // The physics body can stop between grid centers. Check its actual first step instead of
    // repeatedly planning an edge that is clear only from the assumed tile center.
    var corner = to.y > from.y ? new Vec3(from.x, to.y, from.z) : new Vec3(to.x, from.y, to.z);
    for (var segment : List.of(new Vec3[] {from, corner}, new Vec3[] {corner, to})) {
      int samples = Math.max(1, (int) Math.ceil(segment[0].distanceTo(segment[1]) / .2));
      for (int i = 1; i <= samples; i++)
        if (!fits(core, segment[0].lerp(segment[1], (double) i / samples), parts)) return false;
    }
    return true;
  }

  static boolean turnClear(LivingCoreBlockEntity core, double angle) {
    var route = new GroundRoute();
    var offsets = route.offsets(core);
    var center = Vec3.ZERO;
    for (var offset : offsets) center = center.add(offset);
    center = center.scale(1d / Math.max(1, offsets.size()));
    var root = BioshipBlueprint.physical(core, BlockPos.ZERO);
    for (int step = 1; step <= 3; step++) {
      var rotated = new ArrayList<Vec3>();
      for (var offset : offsets)
        rotated.add(offset.subtract(center).yRot((float) (angle * step / 3)).add(center));
      if (!route.fits(core, root, rotated, .71)) return false;
    }
    return true;
  }

  static Vec3 groundedDestination(LivingCoreBlockEntity core, Vec3 requested) {
    var level = core.getLevel();
    var route = new GroundRoute();
    var parts = route.offsets(core);
    double stance = (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset();
    int floor = (int) Math.round(BioshipBlueprint.physical(core, BlockPos.ZERO).y - stance);
    for (int delta : new int[] {0, 1, -1, 2, -2, -3}) {
      var p =
          new BlockPos(
              (int) Math.floor(requested.x), floor + delta - 1, (int) Math.floor(requested.z));
      if (!((ServerLevel) level).getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
      if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)
          || !level.getFluidState(p).isEmpty()) continue;
      var point = new Vec3(p.getX() + .5, p.getY() + 1 + stance, p.getZ() + .5);
      if (route.fits(core, point, parts)) return point;
    }
    return null;
  }

  static boolean flightClear(LivingCoreBlockEntity core, Vec3 target) {
    var route = new GroundRoute();
    var parts = route.offsets(core);
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    for (double distance = 1; distance <= here.distanceTo(target); distance++)
      if (!route.fits(core, here.lerp(target, distance / here.distanceTo(target)), parts))
        return false;
    return route.fits(core, target, parts);
  }

  boolean plan(LivingCoreBlockEntity core, Vec3 goal) {
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var parts = offsets(core);
    double stance = (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset();
    var start =
        new BlockPos(
            (int) Math.floor(here.x), (int) Math.round(here.y - stance), (int) Math.floor(here.z));
    record Visit(BlockPos pos, double cost, double estimate) {}
    var open = new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::estimate));
    Map<BlockPos, Double> costs = new HashMap<>();
    Map<BlockPos, BlockPos> previous = new HashMap<>();
    costs.put(start, 0d);
    open.add(new Visit(start, 0, here.distanceTo(goal)));
    BlockPos end = null, progress = start;
    double closest = here.distanceTo(goal);
    while (!open.isEmpty() && costs.size() < 1600) {
      var node = open.poll();
      if (node.cost() > costs.getOrDefault(node.pos(), Double.MAX_VALUE)) continue;
      Vec3 nodePoint =
          new Vec3(node.pos().getX() + .5, node.pos().getY() + stance, node.pos().getZ() + .5);
      // Search edges still use grid centers, but only the actual body can satisfy the start goal.
      // A hypothetical center can have a clear grasp while the real ship is blocked by a corner.
      var reachPoint = node.pos().equals(start) ? here : nodePoint;
      double remaining = reachPoint.distanceTo(goal);
      if (remaining < closest) {
        closest = remaining;
        progress = node.pos();
      }
      if (remaining < (core.crawl().gathering() ? 3.5 : .8)
          && (!core.crawl().gathering() || BioshipReach.at(core, reachPoint, goal, true))) {
        end = node.pos();
        break;
      }
      if (core.hatchling() && grip(core, nodePoint) != null)
        for (int dy : new int[] {1, -1}) {
          var key = node.pos().offset(0, dy, 0);
          var point = nodePoint.add(0, dy, 0);
          if (Math.abs(key.getY() - start.getY()) > 24
              || grip(core, point) == null
              || !fits(core, point, parts)) continue;
          double cost = node.cost() + 1.4;
          if (cost < costs.getOrDefault(key, Double.MAX_VALUE)) {
            costs.put(key, cost);
            previous.put(key, node.pos());
            open.add(new Visit(key, cost, cost + point.distanceTo(goal)));
          }
        }
      for (var dir : Direction.Plane.HORIZONTAL) {
        var next = node.pos().relative(dir);
        if (Math.abs(next.getX() - start.getX()) > 24 || Math.abs(next.getZ() - start.getZ()) > 24)
          continue;
        var point = standing(core, next.getX(), next.getZ(), node.pos().getY(), parts);
        if (point == null || node.pos().equals(start) && !firstStepClear(core, here, point, parts))
          continue;
        var key = new BlockPos(next.getX(), (int) Math.round(point.y - stance), next.getZ());
        // Prefer clearance around corners. Exact cell-edge paths are fragile for a full physics
        // body.
        double cost =
            node.cost()
                + 1
                + Math.abs(key.getY() - node.pos().getY()) * .5
                + (fits(core, point, parts, .7) ? 0 : 2);
        if (cost >= costs.getOrDefault(key, Double.MAX_VALUE)) continue;
        previous.put(key, node.pos());
        costs.put(key, cost);
        open.add(
            new Visit(
                key, cost, cost + Math.hypot(key.getX() + .5 - goal.x, key.getZ() + .5 - goal.z)));
      }
    }
    plannedAt = core.getLevel().getGameTime();
    destination = goal;
    cursor = 0;
    retryAfter = core.getLevel().getGameTime() + 20;
    complete = end != null;
    if (end == null && closest < here.distanceTo(goal) - 3) end = progress;
    failed = end == null;
    debugStatus =
        failed
            ? "no_route"
            : !complete
                ? "partial_route"
                : end.equals(start)
                    ? core.crawl().gathering() ? "in_action_range" : "at_goal"
                    : "route_ready";
    if (end == null) {
      path = List.of();
      return false;
    }
    var result = new ArrayList<Vec3>();
    while (!end.equals(start)) {
      result.add(new Vec3(end.getX() + .5, end.getY() + stance, end.getZ() + .5));
      end = previous.get(end);
    }
    Collections.reverse(result);
    path = result;
    return true;
  }

  Vec3 next(LivingCoreBlockEntity core, Vec3 goal) {
    if (destination == null
        || destination.distanceToSqr(goal) > .25
        || path.isEmpty() && core.getLevel().getGameTime() >= retryAfter) plan(core, goal);
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    while (cursor < path.size()
        && here.subtract(path.get(cursor)).horizontalDistanceSqr() < .09
        && Math.abs(here.y - path.get(cursor).y) < .3) cursor++;
    if (cursor >= path.size()) {
      if (!path.isEmpty()) debugStatus = complete ? "route_finished" : "segment_finished";
      path = List.of();
      retryAfter = core.getLevel().getGameTime();
      return null;
    }
    var next = path.get(cursor);
    var parts = offsets(core);
    if (!fits(core, next, parts)) {
      debugStatus = "waypoint_blocked";
      path = List.of();
      return null;
    }
    if (core.hatchling() && grip(core, here) == null) {
      var anchor =
          here.add(
              core.ship()
                  .logicalPose()
                  .transformNormal(
                      Vec3.atLowerCornerOf(core.structuralOrigin().subtract(core.getBlockPos()))));
      boolean supported = false;
      var below = BlockPos.containing(anchor);
      for (int i = 1; i <= core.crawl().footingReach(); i++) {
        var p = below.below(i);
        if (core.getLevel().getBlockState(p).isFaceSturdy(core.getLevel(), p, Direction.UP))
          supported = true;
      }
      if (!supported) {
        debugStatus = "unsupported";
        failed = true;
        path = List.of();
        return null;
      }
    }
    // Lift before crossing a one-block step; don't drive the body through its vertical face.
    if (next.y > here.y + .12) {
      if (!fits(core, new Vec3(here.x, next.y, here.z), parts)) {
        debugStatus = "lift_blocked";
        path = List.of();
        return null;
      }
      debugStatus = "lifting";
      if (core.hatchling() && grip(core, here) != null) core.crawl().clingHeight(next.y);
      else core.crawl().stepHeight(next.y);
      return null;
    }
    if (next.subtract(here).horizontalDistanceSqr() < .09 && next.y < here.y - .12) {
      debugStatus = "lowering";
      if (grip(core, here) != null) core.crawl().clingHeight(next.y);
      else core.crawl().lowerHeight(next.y);
      return null;
    }
    if (next.y >= here.y - .12) core.crawl().stepHeight(next.y);
    Vec3 probe = here.lerp(next, Math.min(1, .35 / Math.max(.01, here.distanceTo(next))));
    if (!fits(core, probe, parts)) {
      debugStatus = "first_step_blocked";
      path = List.of();
      return null;
    }
    debugStatus = "following";
    return next;
  }
}
