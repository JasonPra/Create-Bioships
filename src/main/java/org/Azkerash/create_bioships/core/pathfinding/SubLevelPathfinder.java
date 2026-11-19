package org.Azkerash.create_bioships.core.pathfinding;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.locomotion.SubLevelLocomotion;

/** Bounded shared route planning. Movement and limb actuation remain separate. */
public final class SubLevelPathfinder {
  public enum Mode {
    GROUND,
    WATER
  }

  @FunctionalInterface
  public interface Space {
    boolean passable(BlockPos cell, Mode mode);

    default boolean edgeClear(BlockPos from, BlockPos to, Mode mode) {
      return true;
    }
  }

  public record Route(Mode mode, List<Vec3> points) {
    public boolean found() {
      return !points.isEmpty();
    }
  }

  private record Node(BlockPos cell, double score) {}

  private SubLevelPathfinder() {}

  public static boolean allowed(ServerSubLevel body, Mode mode) {
    if (!SubLevelCreatureParts.available(body)) return false;
    body = SubLevelCreatureParts.root(body);
    if (body.isRemoved() || SubLevelHealth.status(body).dead()) return false;
    var flags = SubLevelLocomotion.get(body);
    return mode == Mode.GROUND ? flags.hasLegs() : flags.hasFins();
  }

  public static Vec3 position(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    return body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
  }

  public static Route find(ServerSubLevel body, Vec3 destination, Mode mode) {
    body = SubLevelCreatureParts.root(body);
    if (!allowed(body, mode)) return new Route(mode, List.of());
    var level = body.getLevel();
    var here = position(body);
    var start = BlockPos.containing(here);
    var goal = BlockPos.containing(destination);
    if (here.distanceToSqr(destination) > 20 * 20) return new Route(mode, List.of());
    double groundHeight = here.y;
    if (mode == Mode.GROUND) {
      if (!level.hasChunkAt(start) || !level.hasChunkAt(goal)) return new Route(mode, List.of());
      var a = TerrainProbe.down(level, here.add(0, 2, 0), 12, body);
      var b = TerrainProbe.down(level, destination.add(0, 2, 0), 12, body);
      if (a == null || b == null || !TerrainProbe.standable(a) || !TerrainProbe.standable(b))
        return new Route(mode, List.of());
      start = BlockPos.containing(a.point().add(0, .01, 0));
      goal = BlockPos.containing(b.point().add(0, .01, 0));
      groundHeight = a.point().y;
    }
    var bounds = SubLevelLocomotion.bounds(body).deflate(.025);
    final var box =
        mode == Mode.GROUND
            ? new AABB(
                bounds.minX,
                Math.max(bounds.minY, groundHeight + .05),
                bounds.minZ,
                bounds.maxX,
                bounds.maxY,
                bounds.maxZ)
            : bounds;
    var origin = start;
    var reference = here;
    double stepHeight = mode == Mode.GROUND ? SubLevelLocomotion.stepHeight(body) : 1;
    Space space =
        new Space() {
          private AABB at(double x, double y, double z) {
            return box.move(x + .5 - reference.x, y - origin.getY(), z + .5 - reference.z);
          }

          public boolean passable(BlockPos cell, Mode kind) {
            if (!level.hasChunkAt(cell)) return false;
            if (kind == Mode.GROUND) {
              if (!level.getFluidState(cell).isEmpty()) return false;
              var below = cell.below();
              if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                return false;
            } else if (!level.getFluidState(cell).is(FluidTags.WATER)) return false;
            if (terrainClear(level, at(cell.getX(), cell.getY(), cell.getZ()))) return true;
            // Native stepping starts when the forward feet meet a ledge, before the hull
            // centre crosses it. Reserve that bounded lift instead of treating the swept
            // leg envelope as a solid wall all the way down to the old floor.
            return kind == Mode.GROUND
                && stepHeight > 0
                && terrainClear(level, at(cell.getX(), cell.getY() + stepHeight, cell.getZ()));
          }

          public boolean edgeClear(BlockPos from, BlockPos to, Mode kind) {
            if (kind != Mode.GROUND || from.getY() == to.getY()) return true;
            if (Math.abs(to.getY() - from.getY()) > stepHeight + 1e-6) return false;
            int high = Math.max(from.getY(), to.getY());
            for (double t : new double[] {0, .5, 1})
              if (!terrainClear(
                  level,
                  at(
                      from.getX() + (to.getX() - from.getX()) * t,
                      high,
                      from.getZ() + (to.getZ() - from.getZ()) * t))) return false;
            return true;
          }
        };
    var cells = search(space, start, goal, mode, 256);
    double lift = here.y - origin.getY();
    return new Route(
        mode,
        cells.stream().map(p -> new Vec3(p.getX() + .5, p.getY() + lift, p.getZ() + .5)).toList());
  }

  public static List<BlockPos> search(
      Space world, BlockPos start, BlockPos goal, Mode mode, int budget) {
    if (start.distManhattan(goal) > 32 || budget < 1) return List.of();
    var open = new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));
    var parents = new HashMap<BlockPos, BlockPos>();
    var costs = new HashMap<BlockPos, Double>();
    var memo = new HashMap<BlockPos, Boolean>();
    var closed = new HashSet<BlockPos>();
    costs.put(start, 0d);
    open.add(new Node(start, heuristic(start, goal, mode)));
    while (!open.isEmpty() && closed.size() < Math.min(budget, 1024)) {
      var current = open.remove().cell();
      if (!closed.add(current)) continue;
      if (current.equals(goal)) {
        var result = new ArrayList<BlockPos>();
        for (var p = goal; !p.equals(start); p = parents.get(p)) result.add(p);
        Collections.reverse(result);
        return result.isEmpty() ? List.of(start) : List.copyOf(result);
      }
      var next = new ArrayList<BlockPos>();
      for (var direction : Direction.Plane.HORIZONTAL) {
        var p = current.relative(direction);
        next.add(p);
        if (mode == Mode.GROUND) {
          next.add(p.above());
          next.add(p.below());
        }
      }
      if (mode == Mode.WATER) {
        next.add(current.above());
        next.add(current.below());
      }
      for (var p : next) {
        if (closed.contains(p)
            || Math.abs(p.getY() - start.getY()) > 8
            || p.distManhattan(start) > 32
            || !memo.computeIfAbsent(p, k -> world.passable(k, mode))) continue;
        if (!world.edgeClear(current, p, mode)) continue;
        double cost = costs.get(current) + 1 + Math.abs(p.getY() - current.getY()) * .3;
        if (cost >= costs.getOrDefault(p, Double.POSITIVE_INFINITY)) continue;
        costs.put(p, cost);
        parents.put(p, current);
        open.add(new Node(p, cost + heuristic(p, goal, mode)));
      }
    }
    return List.of();
  }

  private static double heuristic(BlockPos from, BlockPos to, Mode mode) {
    return Math.abs(from.getX() - to.getX())
        + Math.abs(from.getZ() - to.getZ())
        + (mode == Mode.GROUND ? .3 : 1) * Math.abs(from.getY() - to.getY());
  }

  private static boolean terrainClear(net.minecraft.server.level.ServerLevel level, AABB box) {
    var lo = BlockPos.containing(box.minX, box.minY, box.minZ);
    var hi = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
    if (!level.hasChunksAt(lo, hi)) return false;
    for (var p : BlockPos.betweenClosed(lo, hi))
      for (var shape : level.getBlockState(p).getCollisionShape(level, p).toAabbs())
        if (shape.maxX + p.getX() > box.minX
            && shape.minX + p.getX() < box.maxX
            && shape.maxY + p.getY() > box.minY
            && shape.minY + p.getY() < box.maxY
            && shape.maxZ + p.getZ() > box.minZ
            && shape.minZ + p.getZ() < box.maxZ) return false;
    return true;
  }
}
