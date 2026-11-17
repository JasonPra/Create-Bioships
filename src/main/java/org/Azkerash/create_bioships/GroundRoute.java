package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.network.VisionDebug;

import org.Azkerash.create_bioships.integration.sable.BodyCoordinates;

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
    return BioshipSurfaceContacts.grip(core, point);
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
      LivingCoreBlockEntity core,
      int x,
      int z,
      int floorTop,
      List<Vec3> offsets,
      double currentHeight) {
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
    var cling = new Vec3(x + .5, currentHeight, z + .5);
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
    var root = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
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
    int floor = (int) Math.round(BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO).y - stance);
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
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    for (double distance = 1; distance <= here.distanceTo(target); distance++)
      if (!route.fits(core, here.lerp(target, distance / here.distanceTo(target)), parts))
        return false;
    return route.fits(core, target, parts);
  }

  private boolean supportedAt(LivingCoreBlockEntity core, Vec3 point) {
    var anchor =
        point.add(
            core.ship()
                .logicalPose()
                .transformNormal(
                    Vec3.atLowerCornerOf(core.structuralOrigin().subtract(core.getBlockPos()))));
    var level = core.getLevel();
    for (int depth = 1; depth <= core.crawl().footingReach(); depth++) {
      var cell = BlockPos.containing(anchor).below(depth);
      if (level.getBlockState(cell).isFaceSturdy(level, cell, Direction.UP)) return true;
    }
    return grip(core, point) != null;
  }

  private boolean lineClear(
      LivingCoreBlockEntity core, Vec3 from, Vec3 to, List<Vec3> parts, double clearance) {
    if (Math.abs(from.y - to.y) > .5) return false;
    int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .35));
    for (int i = 1; i <= samples; i++) {
      var point = from.lerp(to, (double) i / samples);
      if (!fits(core, point, parts, clearance) || !supportedAt(core, point)) return false;
    }
    return true;
  }

  private List<Vec3> simplify(
      LivingCoreBlockEntity core, Vec3 from, List<Vec3> original, List<Vec3> parts) {
    var result = new ArrayList<Vec3>();
    int index = 0;
    while (index < original.size()) {
      int chosen = index;
      for (int end = original.size() - 1; end > index; end--) {
        if (lineClear(
            core,
            from,
            original.get(end),
            parts,
            LocomotionProfiles.forCreature(core).comfortableClearance())) {
          chosen = end;
          break;
        }
      }
      if (chosen == index)
        for (int end = original.size() - 1; end > index; end--)
          if (lineClear(core, from, original.get(end), parts, .499)) {
            chosen = end;
            break;
          }
      from = original.get(chosen);
      result.add(from);
      index = chosen + 1;
    }
    return List.copyOf(result);
  }

  boolean plan(LivingCoreBlockEntity core, Vec3 goal) {
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
    var parts = offsets(core);
    double stance = (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset();
    var profile = LocomotionProfiles.forCreature(core);
    var delta = goal.subtract(here).multiply(1, 0, 1);
    double travel =
        Math.min(
            profile.directDistance(),
            Math.max(0, delta.length() - (core.crawl().gathering() ? 2.8 : 0)));
    if (travel > .5) {
      var requested = here.add(delta.normalize().scale(travel));
      var ground = groundedDestination(core, requested);
      if (ground != null) {
        var direct = new Vec3(requested.x, ground.y, requested.z);
        boolean finalGrasp =
            !core.crawl().gathering()
                || BioshipReach.at(core, direct, goal, true)
                    && core.assimilation().clusterReachable(core, direct);
        boolean partial = travel >= profile.directDistance() && direct.distanceTo(goal) > 3.5;
        if ((partial || finalGrasp) && lineClear(core, here, direct, parts, .499)) {
          path = List.of(direct);
          destination = goal;
          cursor = 0;
          failed = false;
          complete = travel >= delta.length() - (core.crawl().gathering() ? 3.5 : .8);
          debugStatus = complete ? "direct_route" : "direct_segment";
          plannedAt = core.getLevel().getGameTime();
          retryAfter = plannedAt + 20;
          return true;
        }
      }
    }
    var start =
        new BlockPos(
            (int) Math.floor(here.x), (int) Math.round(here.y - stance), (int) Math.floor(here.z));
    record Visit(BlockPos pos, double cost, double estimate) {}
    var open = new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::estimate));
    Map<BlockPos, Double> costs = new HashMap<>();
    Map<BlockPos, BlockPos> previous = new HashMap<>();
    Map<BlockPos, Vec3> positions = new HashMap<>();
    positions.put(start, here);
    costs.put(start, 0d);
    open.add(new Visit(start, 0, here.distanceTo(goal)));
    BlockPos end = null, progress = start;
    double closest = here.distanceTo(goal);
    boolean surfaces = false;
    boolean retried = false;
    while ((!open.isEmpty() || !retried) && costs.size() < profile.searchNodes()) {
      if (!retried && (open.isEmpty() || costs.size() >= profile.quickNodes())) {
        open.clear();
        costs.clear();
        previous.clear();
        positions.clear();
        positions.put(start, here);
        costs.put(start, 0d);
        open.add(new Visit(start, 0, here.distanceTo(goal)));
        surfaces = true;
        retried = true;
        closest = here.distanceTo(goal);
        progress = start;
      }
      if (open.isEmpty()) break;
      var node = open.poll();
      if (node.cost() > costs.getOrDefault(node.pos(), Double.MAX_VALUE)) continue;
      Vec3 nodePoint = positions.get(node.pos());
      // Search edges still use grid centers, but only the actual body can satisfy the start goal.
      // A hypothetical center can have a clear grasp while the real ship is blocked by a corner.
      var reachPoint = node.pos().equals(start) ? here : nodePoint;
      double remaining = reachPoint.distanceTo(goal);
      if (remaining < closest) {
        closest = remaining;
        progress = node.pos();
      }
      if (remaining < (core.crawl().gathering() ? 3.5 : .8)
          && (!core.crawl().gathering()
              || BioshipReach.at(core, reachPoint, goal, true)
                  && core.assimilation().clusterReachable(core, reachPoint))) {
        end = node.pos();
        break;
      }
      if (surfaces && core.hatchling() && grip(core, nodePoint) != null)
        for (int dy : new int[] {1, -1}) {
          var key = node.pos().offset(0, dy, 0);
          var point = nodePoint.add(0, dy, 0);
          if (Math.abs(key.getY() - start.getY()) > 24
              || grip(core, point) == null
              || !fits(core, point, parts)
              || !firstStepClear(core, nodePoint, point, parts)) continue;
          double cost = node.cost() + 1.4;
          if (cost < costs.getOrDefault(key, Double.MAX_VALUE)) {
            costs.put(key, cost);
            previous.put(key, node.pos());
            positions.put(key, point);
            open.add(new Visit(key, cost, cost + point.distanceTo(goal)));
          }
        }
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          if (dx == 0 && dz == 0) continue;
          var next = node.pos().offset(dx, 0, dz);
          if (Math.abs(next.getX() - start.getX()) > 24
              || Math.abs(next.getZ() - start.getZ()) > 24) continue;
          var point =
              standing(core, next.getX(), next.getZ(), node.pos().getY(), parts, nodePoint.y);
          if (point == null
              || !firstStepClear(core, node.pos().equals(start) ? here : nodePoint, point, parts))
            continue;
          var key = new BlockPos(next.getX(), (int) Math.round(point.y - stance), next.getZ());
          // Prefer clearance around corners. Exact cell-edge paths are fragile for a full physics
          // body.
          double cost =
              node.cost()
                  + Math.hypot(dx, dz)
                  + Math.abs(key.getY() - node.pos().getY()) * .5
                  + (fits(core, point, parts, profile.comfortableClearance()) ? 0 : 2);
          if (cost >= costs.getOrDefault(key, Double.MAX_VALUE)) continue;
          previous.put(key, node.pos());
          positions.put(key, point);
          costs.put(key, cost);
          open.add(
              new Visit(
                  key,
                  cost,
                  cost + Math.hypot(key.getX() + .5 - goal.x, key.getZ() + .5 - goal.z)));
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
      result.add(positions.get(end));
      end = previous.get(end);
    }
    Collections.reverse(result);
    path = simplify(core, here, result, parts);
    return true;
  }

  Vec3 next(LivingCoreBlockEntity core, Vec3 goal) {
    if (destination == null
        || destination.distanceToSqr(goal) > .25
        || path.isEmpty() && core.getLevel().getGameTime() >= retryAfter) plan(core, goal);
    var here = BodyCoordinates.physical(core.amoebaComponents(), BlockPos.ZERO);
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
