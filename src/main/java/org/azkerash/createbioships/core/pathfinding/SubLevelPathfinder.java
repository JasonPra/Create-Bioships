package org.azkerash.createbioships.core.pathfinding;

import dev.letitwalk.walker.TerrainProbe;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.*;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.locomotion.SubLevelLocomotion;

/** Bounded shared route planning. Movement and limb actuation remain separate. */
public final class SubLevelPathfinder {
  public static final int MAX_ROUTE_DISTANCE_BLOCKS = 20;
  private static final int ROUTE_SEARCH_NODE_BUDGET = 256;
  private static final double ROUTE_CLEARANCE_MARGIN_BLOCKS = .3;
  private static final int MAX_VISIBILITY_TESTS = 64;
  private static final int MIN_VERTICAL_SEARCH_RANGE = 8;
  private static final int VERTICAL_SEARCH_MARGIN = 2;
  private static final int GROUND_PROBE_DEPTH_BLOCKS = 12;
  private static final int GROUND_PROBE_ORIGIN_LIFT_BLOCKS = 2;

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

  private static final List<BlockPos> GROUND_OFFSETS = offsets(false),
      WATER_OFFSETS = offsets(true);

  private static List<BlockPos> offsets(boolean water) {
    var result = new ArrayList<BlockPos>();
    for (int x = -1; x <= 1; x++) {
      for (int y = -1; y <= 1; y++) {
        for (int z = -1; z <= 1; z++) {
          if (x == 0 && z == 0 && (!water || y == 0)) {
            continue;
          }
          result.add(new BlockPos(x, y, z));
        }
      }
    }
    return List.copyOf(result);
  }

  private SubLevelPathfinder() {}

  public static boolean allowed(ServerSubLevel body, Mode mode) {
    if (!SubLevelCreatureParts.available(body)) {
      return false;
    }
    body = SubLevelCreatureParts.root(body);
    if (body.isRemoved() || SubLevelHealth.status(body).dead()) {
      return false;
    }
    var flags = SubLevelLocomotion.get(body);
    return mode == Mode.GROUND ? flags.hasLegs() : flags.hasFins();
  }

  public static Vec3 position(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    return body.logicalPose().transformPosition(Vec3.atCenterOf(body.getPlot().getCenterBlock()));
  }

  public static Route find(ServerSubLevel body, Vec3 destination, Mode mode) {
    body = SubLevelCreatureParts.root(body);
    if (!allowed(body, mode)) {
      return new Route(mode, List.of());
    }
    var level = body.getLevel();
    var here = position(body);
    var start = BlockPos.containing(here);
    var goal = BlockPos.containing(destination);
    if (here.distanceToSqr(destination) > MAX_ROUTE_DISTANCE_BLOCKS * MAX_ROUTE_DISTANCE_BLOCKS) {
      return new Route(mode, List.of());
    }
    double groundHeight = here.y;
    if (mode == Mode.GROUND) {
      if (!level.hasChunkAt(start) || !level.hasChunkAt(goal)) {
        return new Route(mode, List.of());
      }
      var a =
          TerrainProbe.down(
              level,
              here.add(0, GROUND_PROBE_ORIGIN_LIFT_BLOCKS, 0),
              GROUND_PROBE_DEPTH_BLOCKS,
              body);
      var b =
          TerrainProbe.down(
              level,
              destination.add(0, GROUND_PROBE_ORIGIN_LIFT_BLOCKS, 0),
              GROUND_PROBE_DEPTH_BLOCKS,
              body);
      if (a == null || b == null || !TerrainProbe.standable(a) || !TerrainProbe.standable(b)) {
        return new Route(mode, List.of());
      }
      start = BlockPos.containing(a.point().add(0, .01, 0));
      goal = BlockPos.containing(b.point().add(0, .01, 0));
      groundHeight = a.point().y;
    }
    var bounds =
        SubLevelLocomotion.bounds(body)
            .inflate(ROUTE_CLEARANCE_MARGIN_BLOCKS, 0, ROUTE_CLEARANCE_MARGIN_BLOCKS)
            .deflate(.025);
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
    // Capture once per search; moving constructs are recaptured by the next search/live check.
    var obstacles = SubLevelObstacles.capture(body, box.inflate(MAX_ROUTE_DISTANCE_BLOCKS));
    var footprint =
        mode == Mode.GROUND && !SubLevelLocomotion.get(body).hasFins()
            ? new NavigationFootprint(body)
            : null;
    var terrain = new NavigationTerrainSnapshot(level, obstacles, footprint);
    var origin = start;
    var reference = here;
    double stepHeight = mode == Mode.GROUND ? SubLevelLocomotion.stepHeight(body) : 1;
    Space space =
        new Space() {
          private AABB at(double x, double y, double z) {
            return box.move(x + .5 - reference.x, y - origin.getY(), z + .5 - reference.z);
          }

          private boolean clear(AABB query) {
            var offset =
                new Vec3(query.minX - box.minX, query.minY - box.minY, query.minZ - box.minZ);
            return terrain.clearPose(
                query,
                offset,
                box.minY + offset.y,
                footprint == null ? 0 : ROUTE_CLEARANCE_MARGIN_BLOCKS,
                true);
          }

          public boolean passable(BlockPos cell, Mode kind) {
            if (!level.hasChunkAt(cell)) {
              return false;
            }
            if (kind == Mode.GROUND) {
              if (!level.getFluidState(cell).isEmpty()
                  && !level.getFluidState(cell).is(FluidTags.WATER)) {
                return false;
              }
              var below = cell.below();
              if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return false;
              }
            } else if (!level.getFluidState(cell).is(FluidTags.WATER)) {
              return false;
            }
            if (clear(at(cell.getX(), cell.getY(), cell.getZ()))) {
              return true;
            }
            // Native stepping starts when the forward feet meet a ledge, before the hull
            // centre crosses it. Reserve that bounded lift instead of treating the swept
            // leg envelope as a solid wall all the way down to the old floor.
            return kind == Mode.GROUND
                && stepHeight > 0
                && clear(at(cell.getX(), cell.getY() + stepHeight, cell.getZ()));
          }

          private boolean swept(AABB from, AABB to) {
            for (double t : new double[] {0, .25, .5, .75, 1}) {
              if (!clear(
                  from.move(
                      (to.minX - from.minX) * t,
                      (to.minY - from.minY) * t,
                      (to.minZ - from.minZ) * t))) {
                return false;
              }
            }
            return true;
          }

          public boolean edgeClear(BlockPos from, BlockPos to, Mode kind) {
            if (kind == Mode.GROUND && Math.abs(to.getY() - from.getY()) > stepHeight + 1e-6) {
              return false;
            }
            // Begin at the actual pose, not a rounded grid centre that could overlap a wall.
            var begin = from.equals(origin) ? box : at(from.getX(), from.getY(), from.getZ());
            var end = at(to.getX(), to.getY(), to.getZ());
            if (kind == Mode.GROUND && from.getY() != to.getY()) {
              int high = Math.max(from.getY(), to.getY());
              begin = begin.move(0, high - from.getY(), 0);
              end = end.move(0, high - to.getY(), 0);
            }
            if (swept(begin, end)) {
              return true;
            }
            // Front feet reach a stair before the hull centre does; reserve native lift.
            return kind == Mode.GROUND
                && from.getY() == to.getY()
                && stepHeight > 0
                && swept(begin.move(0, stepHeight, 0), end.move(0, stepHeight, 0));
          }
        };
    var cells =
        visible(space, start, goal, mode, new HashMap<>())
            ? List.of(goal)
            : waypoints(
                space, start, search(space, start, goal, mode, ROUTE_SEARCH_NODE_BUDGET), mode);
    double lift = here.y - origin.getY();
    return new Route(
        mode,
        cells.stream().map(p -> new Vec3(p.getX() + .5, p.getY() + lift, p.getZ() + .5)).toList());
  }

  /**
   * Keep only visible route bends; intermediate cells remain collision/support samples, not
   * steering targets.
   */
  public static List<BlockPos> waypoints(
      Space world, BlockPos start, List<BlockPos> cells, Mode mode) {
    if (cells.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<BlockPos>();
    var anchor = start;
    int next = 0;
    int visibilityBudget = MAX_VISIBILITY_TESTS;
    var memo = new HashMap<BlockPos, Boolean>();
    while (next < cells.size()) {
      int chosen = next;
      for (int end = cells.size() - 1; end > next && visibilityBudget > 0; end--) {
        visibilityBudget--;
        if (visible(world, anchor, cells.get(end), mode, memo)) {
          chosen = end;
          break;
        }
      }
      anchor = cells.get(chosen);
      result.add(anchor);
      next = chosen + 1;
    }
    return List.copyOf(result);
  }

  private static boolean visible(
      Space world, BlockPos from, BlockPos to, Mode mode, Map<BlockPos, Boolean> memo) {
    // Quarter-block samples retain thin diagonals and ground gaps while skipping redundant targets.
    final int samplesPerBlock = 4;
    int samples = Math.max(1, (int) Math.ceil(Math.sqrt(from.distSqr(to)) * samplesPerBlock));
    var previous = from;
    for (int sample = 1; sample <= samples; sample++) {
      double t = sample / (double) samples;
      var cell =
          BlockPos.containing(
              from.getX() + .5 + (to.getX() - from.getX()) * t,
              from.getY() + .5 + (to.getY() - from.getY()) * t,
              from.getZ() + .5 + (to.getZ() - from.getZ()) * t);
      if (cell.equals(previous)) {
        continue;
      }
      if (!memo.computeIfAbsent(cell, key -> world.passable(key, mode))
          || !cornerClear(world, previous, cell, mode, memo)
          || !world.edgeClear(previous, cell, mode)) {
        return false;
      }
      previous = cell;
    }
    return previous.equals(to);
  }

  /**
   * Recheck live articulated clearance; a saved route is not permission to push through terrain.
   */
  public static boolean movementClear(ServerSubLevel body, Vec3 movement, double yawRate) {
    body = SubLevelCreatureParts.root(body);
    if (movement.lengthSqr() < 1e-8 && Math.abs(yawRate) < 1e-8) {
      return true;
    }
    var level = body.getLevel();
    var wallBounds = org.azkerash.createbioships.core.legs.SubLevelLegs.wallMotionBounds(body);
    var box = (wallBounds == null ? SubLevelLocomotion.bounds(body) : wallBounds).deflate(.025);
    var here = position(body);
    var ground =
        TerrainProbe.down(
            level,
            here.add(0, GROUND_PROBE_ORIGIN_LIFT_BLOCKS, 0),
            GROUND_PROBE_DEPTH_BLOCKS,
            body);
    if (ground != null && TerrainProbe.standable(ground)) {
      box =
          new AABB(
              box.minX,
              Math.max(box.minY, ground.point().y + .05),
              box.minZ,
              box.maxX,
              box.maxY,
              box.maxZ);
    }
    // Reserve half a second of motion, plus finite swing/turn clearance.
    double radius = Math.max(box.getXsize(), box.getZsize()) / 2;
    double margin = .15 + Math.min(.4, radius * Math.abs(yawRate) * .1);
    var delta = movement.scale(.5);
    var flags = SubLevelLocomotion.get(body);
    var footprint = flags.hasLegs() && !flags.hasFins() ? new NavigationFootprint(body) : null;
    if (movementSweepClear(body, box, delta, margin, footprint, 0, wallBounds == null)) {
      return true;
    }
    // Permit bounded native step-up, while keeping tall walls blocked.
    double step = SubLevelLocomotion.stepHeight(body);
    return step > 0
        && movementSweepClear(
            body, box.move(0, step, 0), delta, margin, footprint, step, wallBounds == null);
  }

  private static double overlap(AABB a, AABB b) {
    return Math.max(0, Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX))
        * Math.max(0, Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY))
        * Math.max(0, Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ));
  }

  private static boolean movementSweepClear(
      ServerSubLevel body,
      AABB start,
      Vec3 delta,
      double margin,
      NavigationFootprint footprint,
      double lift,
      boolean includeLegs) {
    var level = body.getLevel();
    var future = start.move(delta).inflate(margin, 0, margin);
    var sweep = start.minmax(future);
    var obstacles = SubLevelObstacles.capture(body, sweep);
    if (clear(level, obstacles, sweep)) {
      return true;
    }
    var precise = new NavigationTerrainSnapshot(level, obstacles, footprint);
    boolean preciseClear = footprint != null;
    for (double fraction : preciseClear ? new double[] {0, .25, .5, .75, 1} : new double[0]) {
      var offset = delta.scale(fraction);
      if (!precise.clearPose(
          start.move(offset).inflate(margin, 0, margin),
          offset.add(0, lift, 0),
          start.minY,
          margin,
          includeLegs)) {
        preciseClear = false;
        break;
      }
    }
    if (preciseClear) {
      return true;
    }
    // Physics contact tolerance can leave a tiny starting overlap. Permit only escape:
    // the destination is clear, no new obstacle is crossed, and overlap never increases.
    if (delta.lengthSqr() < 1e-8 || !clear(level, obstacles, future)) {
      return false;
    }
    var lo = BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ);
    var hi = BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ);
    if (!level.hasChunksAt(lo, hi)) {
      return false;
    }
    for (var obstacle : obstacles.boxes()) {
      if (!escapeClear(start, delta, sweep, obstacle)) {
        return false;
      }
    }
    for (var cell : BlockPos.betweenClosed(lo, hi)) {
      for (var shape : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs()) {
        var obstacle = shape.move(cell);
        if (!escapeClear(start, delta, sweep, obstacle)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean escapeClear(AABB start, Vec3 delta, AABB sweep, AABB obstacle) {
    if (!obstacle.intersects(sweep)) {
      return true;
    }
    double previous = overlap(start, obstacle);
    if (previous <= 0) {
      return false;
    }
    for (double t : new double[] {.25, .5, .75, 1}) {
      double current = overlap(start.move(delta.scale(t)), obstacle);
      if (current > previous + 1e-8) {
        return false;
      }
      previous = current;
    }
    return true;
  }

  private static boolean clear(
      net.minecraft.server.level.ServerLevel level,
      SubLevelObstacles.Snapshot obstacles,
      AABB box) {
    return terrainClear(level, box) && obstacles.clear(box);
  }

  public static List<BlockPos> search(
      Space world, BlockPos start, BlockPos goal, Mode mode, int budget) {
    if (start.distManhattan(goal) > 32 || budget < 1) {
      return List.of();
    }
    // Include the destination's elevation; a fixed ±8 band rejected otherwise valid descents.
    int verticalRange =
        Math.max(
            MIN_VERTICAL_SEARCH_RANGE,
            Math.abs(goal.getY() - start.getY()) + VERTICAL_SEARCH_MARGIN);
    var open = new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));
    var parents = new HashMap<BlockPos, BlockPos>();
    var costs = new HashMap<BlockPos, Double>();
    var memo = new HashMap<BlockPos, Boolean>();
    var closed = new HashSet<BlockPos>();
    costs.put(start, 0d);
    open.add(new Node(start, heuristic(start, goal, mode)));
    while (!open.isEmpty() && closed.size() < Math.min(budget, 1024)) {
      var current = open.remove().cell();
      if (!closed.add(current)) {
        continue;
      }
      if (current.equals(goal)) {
        var result = new ArrayList<BlockPos>();
        for (var p = goal; !p.equals(start); p = parents.get(p)) {
          result.add(p);
        }
        Collections.reverse(result);
        return result.isEmpty() ? List.of(start) : List.copyOf(result);
      }
      for (var offset : mode == Mode.GROUND ? GROUND_OFFSETS : WATER_OFFSETS) {
        var p = current.offset(offset);
        if (closed.contains(p)
            || Math.abs(p.getY() - start.getY()) > verticalRange
            || p.distManhattan(start) > 32
            || !memo.computeIfAbsent(p, k -> world.passable(k, mode))) {
          continue;
        }
        if (!cornerClear(world, current, p, mode, memo) || !world.edgeClear(current, p, mode)) {
          continue;
        }
        double edge =
            mode == Mode.GROUND
                ? Math.hypot(offset.getX(), offset.getZ()) + Math.abs(offset.getY()) * .3
                : Math.sqrt(
                    offset.getX() * offset.getX()
                        + offset.getY() * offset.getY()
                        + offset.getZ() * offset.getZ());
        double cost = costs.get(current) + edge;
        if (cost >= costs.getOrDefault(p, Double.POSITIVE_INFINITY)) {
          continue;
        }
        costs.put(p, cost);
        parents.put(p, current);
        open.add(new Node(p, cost + heuristic(p, goal, mode)));
      }
    }
    return List.of();
  }

  /** Every crossed side/corner cell must be clear; a free diagonal endpoint is not enough. */
  private static boolean cornerClear(
      Space world, BlockPos from, BlockPos to, Mode mode, Map<BlockPos, Boolean> memo) {
    int dx = to.getX() - from.getX(),
        dy = mode == Mode.WATER ? to.getY() - from.getY() : 0,
        dz = to.getZ() - from.getZ();
    int mask = (dx != 0 ? 1 : 0) | (dy != 0 ? 2 : 0) | (dz != 0 ? 4 : 0);
    if (Integer.bitCount(mask) < 2) {
      return true;
    }
    for (int subset = (mask - 1) & mask; subset != 0; subset = (subset - 1) & mask) {
      var side =
          from.offset(
              (subset & 1) != 0 ? dx : 0, (subset & 2) != 0 ? dy : 0, (subset & 4) != 0 ? dz : 0);
      if (!memo.computeIfAbsent(side, p -> world.passable(p, mode))) {
        return false;
      }
    }
    return true;
  }

  private static double heuristic(BlockPos from, BlockPos to, Mode mode) {
    int x = Math.abs(from.getX() - to.getX()),
        y = Math.abs(from.getY() - to.getY()),
        z = Math.abs(from.getZ() - to.getZ());
    if (mode == Mode.GROUND) {
      return Math.max(x, z) + (Math.sqrt(2) - 1) * Math.min(x, z) + .3 * y;
    }
    int lo = Math.min(x, Math.min(y, z)),
        hi = Math.max(x, Math.max(y, z)),
        mid = x + y + z - lo - hi;
    return Math.sqrt(3) * lo + Math.sqrt(2) * (mid - lo) + (hi - mid);
  }

  private static boolean terrainClear(net.minecraft.server.level.ServerLevel level, AABB box) {
    var lo = BlockPos.containing(box.minX, box.minY, box.minZ);
    var hi = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
    if (!level.hasChunksAt(lo, hi)) {
      return false;
    }
    for (var p : BlockPos.betweenClosed(lo, hi)) {
      for (var shape : level.getBlockState(p).getCollisionShape(level, p).toAabbs()) {
        if (shape.maxX + p.getX() > box.minX
            && shape.minX + p.getX() < box.maxX
            && shape.maxY + p.getY() > box.minY
            && shape.minY + p.getY() < box.maxY
            && shape.maxZ + p.getZ() > box.minZ
            && shape.minZ + p.getZ() < box.maxZ) {
          return false;
        }
      }
    }
    return true;
  }
}
