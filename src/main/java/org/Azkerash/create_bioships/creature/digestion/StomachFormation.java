package org.Azkerash.create_bioships.creature.digestion;

import java.util.*;
import org.Azkerash.create_bioships.creature.digestion.DigestiveNetwork.Cell;

/** Derived solid-cube membership. Never stores or replaces organ inventories. */
public final class StomachFormation {
  public record Cube<T>(Cell origin, Map<Cell, T> members) {
    public Cube {
      members = Map.copyOf(members);
    }

    public boolean contains(Cell cell) {
      return members.containsKey(cell);
    }
  }

  public static List<Cell> cells(Cell origin) {
    var result = new ArrayList<Cell>(27);
    for (int x = 0; x < 3; x++)
      for (int y = 0; y < 3; y++)
        for (int z = 0; z < 3; z++)
          result.add(new Cell(origin.x() + x, origin.y() + y, origin.z() + z));
    return List.copyOf(result);
  }

  public static Set<Cell> origins(Collection<Cell> stomachs) {
    var result = new TreeSet<Cell>();
    for (var p : stomachs)
      for (int x = 0; x < 3; x++)
        for (int y = 0; y < 3; y++)
          for (int z = 0; z < 3; z++) result.add(new Cell(p.x() - x, p.y() - y, p.z() - z));
    return Collections.unmodifiableSet(result);
  }

  /** Stable, non-overlapping cubes; a member can never receive two formation bonuses. */
  public static <T> List<Cube<T>> detect(Map<Cell, T> stomachs) {
    if (stomachs.size() < 27) return List.of();
    var used = new HashSet<Cell>();
    var result = new ArrayList<Cube<T>>();
    for (var origin : origins(stomachs.keySet())) {
      var cells = cells(origin);
      if (cells.stream().anyMatch(p -> used.contains(p) || !stomachs.containsKey(p))) continue;
      var members = new LinkedHashMap<Cell, T>();
      for (var p : cells) members.put(p, Objects.requireNonNull(stomachs.get(p)));
      result.add(new Cube<>(origin, members));
      used.addAll(cells);
    }
    return List.copyOf(result);
  }

  /** Five units in four ticks: 25% faster, with no extra recipe yield or ingredient changes. */
  public static int workUnits(long tick, boolean formed) {
    return formed && (tick & 3L) == 0 ? 2 : 1;
  }

  private StomachFormation() {}
}
