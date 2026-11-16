package org.Azkerash.create_bioships;

import java.util.*;

/** Plain resource accounting, independent of block entities and behavior selection. */
final class MaterialLedger implements MaterialCost.Account {
  private final Map<String, Integer> amounts = new HashMap<>();

  public int material(String id) {
    return amounts.getOrDefault(id, 0);
  }

  public boolean spend(String id, int count) {
    if (count < 0 || material(id) < count) return false;
    int remaining = material(id) - count;
    if (remaining == 0) amounts.remove(id);
    else amounts.put(id, remaining);
    return true;
  }

  public void credit(String id, int count) {
    if (count < 0) throw new IllegalArgumentException("Negative credit");
    if (count > 0) amounts.merge(id, count, Math::addExact);
  }

  Map<String, Integer> snapshot() {
    return Map.copyOf(amounts);
  }

  void restore(Map<String, Integer> saved) {
    amounts.clear();
    saved.forEach(
        (id, count) -> {
          if (count > 0) amounts.put(id, Math.min(count, 1000000));
        });
  }
}
