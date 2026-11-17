package org.Azkerash.create_bioships.creature.navigation;

import java.util.*;
import java.util.function.*;

/** Bounded 3D navigation, independent of Minecraft, organs and propulsion. */
public final class WaterRoutePlanner {
  public record Point(double x, double y, double z) {
    public double distance(Point other) {
      return Math.sqrt(
          Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2));
    }

    public Point lerp(Point other, double t) {
      return new Point(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
    }
  }

  public interface Space {
    boolean fits(Point point);

    boolean clear(Point from, Point to);
  }

  public enum Status {
    COMPLETE,
    SEGMENT,
    UNREACHABLE,
    BUDGET_EXHAUSTED
  }

  public record Result(List<Point> points, Status status, int expanded) {
    public Result {
      points = List.copyOf(points);
    }
  }

  private record Cell(int x, int y, int z) {
    static Cell at(Point p) {
      return new Cell((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
    }

    Point center() {
      return new Point(x + .5, y + .5, z + .5);
    }
  }

  private record Entry(Cell cell, double cost, double estimate, long order) {}

  public static Result toTarget(
      Point start, Point target, double arrival, Space space, int budget, int radius) {
    if (!Double.isFinite(start.distance(target)) || arrival < 0 || !Double.isFinite(arrival))
      throw new IllegalArgumentException("Non-finite route or invalid arrival radius");
    if (budget < 1 || radius < 1)
      throw new IllegalArgumentException("Positive search limits required");
    double distance = start.distance(target);
    if (distance <= arrival) return new Result(List.of(start), Status.COMPLETE, 0);
    var approach = start.lerp(target, Math.max(0, (distance - arrival * .8) / distance));
    if (space.fits(approach) && space.clear(start, approach))
      return new Result(List.of(start, approach), Status.COMPLETE, 0);
    boolean segment = distance > radius * .75;
    var goal = segment ? start.lerp(target, radius * .75 / distance) : target;
    double tolerance = segment ? Math.max(1.1, arrival) : arrival;
    var result =
        find(
            start,
            p -> p.distance(goal) <= tolerance,
            p -> Math.max(0, p.distance(goal) - tolerance),
            space,
            budget,
            radius);
    return segment && result.status() == Status.COMPLETE
        ? new Result(result.points(), Status.SEGMENT, result.expanded())
        : result;
  }

  public static Result find(
      Point start,
      Predicate<Point> goal,
      ToDoubleFunction<Point> heuristic,
      Space space,
      int budget,
      int radius) {
    if (budget < 1 || radius < 1)
      throw new IllegalArgumentException("Positive search limits required");
    if (goal.test(start)) return new Result(List.of(start), Status.COMPLETE, 0);
    var origin = Cell.at(start);
    var queue =
        new PriorityQueue<Entry>(
            Comparator.comparingDouble(Entry::estimate).thenComparingLong(Entry::order));
    var costs = new HashMap<Cell, Double>();
    var parents = new HashMap<Cell, Cell>();
    var positions = new HashMap<Cell, Point>();
    var fits = new HashMap<Cell, Boolean>();
    costs.put(origin, 0d);
    positions.put(origin, start);
    queue.add(new Entry(origin, 0, heuristic.applyAsDouble(start), 0));
    int expanded = 0;
    long order = 1;
    while (!queue.isEmpty() && expanded < budget) {
      var entry = queue.remove();
      var current = entry.cell;
      if (entry.cost > costs.getOrDefault(current, Double.POSITIVE_INFINITY)) continue;
      var from = positions.get(current);
      expanded++;
      if (goal.test(from)) {
        var path = new ArrayList<Point>();
        var cell = current;
        while (cell != null) {
          path.add(positions.get(cell));
          cell = parents.get(cell);
        }
        Collections.reverse(path);
        return new Result(smooth(path, space), Status.COMPLETE, expanded);
      }
      for (int dx = -1; dx <= 1; dx++)
        for (int dy = -1; dy <= 1; dy++)
          for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            var next = new Cell(current.x + dx, current.y + dy, current.z + dz);
            var point = next.center();
            if (start.distance(point) > radius
                || !fits.computeIfAbsent(next, c -> space.fits(c.center()))) continue;
            double cost = entry.cost + from.distance(point);
            if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
            // Every axis subset must fit: no diagonal cutting between touching obstacles.
            boolean corner = true;
            for (int mask = 1; mask < 7 && corner; mask++) {
              int x = (mask & 1) == 0 ? 0 : dx,
                  y = (mask & 2) == 0 ? 0 : dy,
                  z = (mask & 4) == 0 ? 0 : dz;
              if (x == 0 && y == 0 && z == 0 || x == dx && y == dy && z == dz) continue;
              var partial = new Cell(current.x + x, current.y + y, current.z + z);
              corner = fits.computeIfAbsent(partial, c -> space.fits(c.center()));
            }
            if (!corner || !space.clear(from, point)) continue;
            costs.put(next, cost);
            parents.put(next, current);
            positions.put(next, point);
            queue.add(new Entry(next, cost, cost + heuristic.applyAsDouble(point), order++));
          }
    }
    return new Result(
        List.of(), queue.isEmpty() ? Status.UNREACHABLE : Status.BUDGET_EXHAUSTED, expanded);
  }

  private static List<Point> smooth(List<Point> path, Space space) {
    var result = new ArrayList<Point>();
    int cursor = 0;
    result.add(path.getFirst());
    while (cursor < path.size() - 1) {
      int next = Math.min(path.size() - 1, cursor + 32);
      while (next > cursor + 1 && !space.clear(path.get(cursor), path.get(next))) next--;
      result.add(path.get(next));
      cursor = next;
    }
    return result;
  }

  private WaterRoutePlanner() {}
}
