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

  boolean failed() {
    return failed;
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
    var level = (ServerLevel) core.getLevel();
    for (var offset : offsets) {
      Vec3 center = point.add(offset);
      for (var cell :
          BlockPos.betweenClosed(
              BlockPos.containing(center.add(-.45, -.45, -.45)),
              BlockPos.containing(center.add(.45, .45, .45))))
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
    }
    var cling =
        new Vec3(
            x + .5, floorTop + (core.hatchling() ? .85 : 1.25) + core.coreHeightOffset(), z + .5);
    return core.hatchling() && grip(core, cling) != null && fits(core, cling, offsets)
        ? cling
        : null;
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
    BlockPos end = null;
    while (!open.isEmpty() && costs.size() < 1600) {
      var node = open.poll();
      if (node.cost() > costs.getOrDefault(node.pos(), Double.MAX_VALUE)) continue;
      Vec3 nodePoint =
          new Vec3(node.pos().getX() + .5, node.pos().getY() + stance, node.pos().getZ() + .5);
      if (nodePoint.distanceTo(goal) < (core.crawl().gathering() ? 3.5 : .8)) {
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
        if (point == null) continue;
        var key = new BlockPos(next.getX(), (int) Math.round(point.y - stance), next.getZ());
        double cost = node.cost() + 1 + Math.abs(key.getY() - node.pos().getY()) * .5;
        if (cost >= costs.getOrDefault(key, Double.MAX_VALUE)) continue;
        previous.put(key, node.pos());
        costs.put(key, cost);
        open.add(
            new Visit(
                key, cost, cost + Math.hypot(key.getX() + .5 - goal.x, key.getZ() + .5 - goal.z)));
      }
    }
    destination = goal;
    cursor = 0;
    retryAfter = core.getLevel().getGameTime() + 20;
    failed = end == null;
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
    if (cursor >= path.size()) return null;
    var next = path.get(cursor);
    var parts = offsets(core);
    if (!fits(core, next, parts)) {
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
      for (int i = 1; i <= 3; i++) {
        var p = below.below(i);
        if (core.getLevel().getBlockState(p).isFaceSturdy(core.getLevel(), p, Direction.UP))
          supported = true;
      }
      if (!supported) {
        failed = true;
        path = List.of();
        return null;
      }
    }
    // Lift before crossing a one-block step; don't drive the body through its vertical face.
    if (next.y > here.y + .12) {
      if (!fits(core, new Vec3(here.x, next.y, here.z), parts)) {
        path = List.of();
        return null;
      }
      if (core.hatchling() && grip(core, here) != null) core.crawl().clingHeight(next.y);
      else core.crawl().stepHeight(next.y);
      return null;
    }
    if (next.subtract(here).horizontalDistanceSqr() < .09
        && next.y < here.y - .12
        && grip(core, here) != null) {
      core.crawl().clingHeight(next.y);
      return null;
    }
    Vec3 probe = here.lerp(next, Math.min(1, .35 / Math.max(.01, here.distanceTo(next))));
    if (!fits(core, probe, parts)) {
      path = List.of();
      return null;
    }
    return next;
  }
}
