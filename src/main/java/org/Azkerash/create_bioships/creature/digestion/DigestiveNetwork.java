package org.Azkerash.create_bioships.creature.digestion;

import java.util.*;
import java.util.function.Predicate;

/** Immutable face-connected stomach topology. No core, inventory or Minecraft runtime dependency. */
public final class DigestiveNetwork<T> {
  public record Cell(int x, int y, int z) implements Comparable<Cell> {
    public List<Cell> neighbors() {
      return List.of(new Cell(x-1,y,z),new Cell(x+1,y,z),new Cell(x,y-1,z),
          new Cell(x,y+1,z),new Cell(x,y,z-1),new Cell(x,y,z+1));
    }
    @Override public int compareTo(Cell other) {
      int order=Integer.compare(x,other.x);
      if(order==0)order=Integer.compare(y,other.y);
      return order!=0?order:Integer.compare(z,other.z);
    }
  }

  private final Map<Cell, List<T>> membership;
  private final List<List<T>> groups;

  public DigestiveNetwork(Map<Cell,T> stomachs) {
    var nodes=new TreeMap<>(stomachs);
    nodes.values().forEach(Objects::requireNonNull);
    var assigned=new HashMap<Cell,List<T>>();
    var components=new ArrayList<List<T>>();
    while(!nodes.isEmpty()) {
      var queue=new ArrayDeque<Cell>();
      var cells=new ArrayList<Cell>();
      var members=new ArrayList<T>();
      var first=nodes.firstKey();
      members.add(Objects.requireNonNull(nodes.remove(first)));
      queue.add(first);
      while(!queue.isEmpty()) {
        var cell=queue.removeFirst();
        cells.add(cell);
        for(var neighbor:cell.neighbors()) {
          var member=nodes.remove(neighbor);
          if(member!=null) {members.add(member);queue.addLast(neighbor);}
        }
      }
      var group=List.copyOf(members);
      components.add(group);
      for(var cell:cells)assigned.put(cell,group);
    }
    membership=Map.copyOf(assigned);
    groups=List.copyOf(components);
  }

  public List<T> members(Cell cell) { return membership.getOrDefault(cell,List.of()); }
  public List<List<T>> groups() { return groups; }

  /** An external connection belongs to the group; it is not copied into every member. */
  public boolean hasConnection(Cell cell, Predicate<T> directlyConnected) {
    return members(cell).stream().anyMatch(directlyConnected);
  }
}
