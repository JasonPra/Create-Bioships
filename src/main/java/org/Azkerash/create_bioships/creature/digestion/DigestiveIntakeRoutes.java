package org.Azkerash.create_bioships.creature.digestion;

import java.util.*;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell;

/** Physical intake routes. Mouths are inputs, tubes are transit, stomachs are destinations. */
public final class DigestiveIntakeRoutes<T> {
  public record Route<T>(Cell mouth, T stomach, List<Cell> tubes) {
    public Route {
      tubes = List.copyOf(tubes);
    }
  }

  private final Map<Cell, List<Route<T>>> routes;
  private final Set<Cell> mouths;

  public DigestiveIntakeRoutes(
      Map<Cell, T> stomachs, Set<Cell> mouths, Set<Cell> tubes, Map<Cell, Set<Cell>> links) {
    this.mouths = Set.copyOf(mouths);
    var found = new TreeMap<Cell, List<Route<T>>>();
    for (var mouth : new TreeSet<>(mouths)) {
      var destinations = new ArrayList<Route<T>>();
      var parents = new HashMap<Cell, Cell>();
      var queue = new ArrayDeque<Cell>();
      parents.put(mouth, mouth);
      queue.add(mouth);
      while (!queue.isEmpty()) {
        var point = queue.removeFirst();
        var stomach = stomachs.get(point);
        if (stomach != null) {
          var path = new ArrayList<Cell>();
          var previous = parents.get(point);
          while (!previous.equals(mouth)) {
            path.add(previous);
            previous = parents.get(previous);
          }
          Collections.reverse(path);
          destinations.add(new Route<>(mouth, stomach, path));
          continue;
        }
        for (var next : new TreeSet<>(links.getOrDefault(point, Set.of())))
          if ((tubes.contains(next) || stomachs.containsKey(next))
              && parents.putIfAbsent(next, point) == null) queue.addLast(next);
      }
      if (!destinations.isEmpty()) found.put(mouth, List.copyOf(destinations));
    }
    routes = Map.copyOf(found);
  }

  public Set<Cell> mouths() {
    return mouths;
  }

  public List<Route<T>> routes(Cell mouth) {
    return routes.getOrDefault(mouth, List.of());
  }

  public Route<T> route(Cell mouth) {
    return routes(mouth).isEmpty() ? null : routes(mouth).getFirst();
  }

  public List<Route<T>> routes() {
    return routes.values().stream().flatMap(List::stream).toList();
  }
}
