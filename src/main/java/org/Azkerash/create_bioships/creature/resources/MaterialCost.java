package org.Azkerash.create_bioships.creature.resources;

import java.util.*;

/** Resource transactions shared by storage, emergency reserves, growth and routing. */
public record MaterialCost(int flesh, int fiber, int bone) {
  public static final String FLESH = "biomancy:flesh_bits",
      FIBER = "biomancy:elastic_fibers",
      BONE = "biomancy:bone_fragments";

  public MaterialCost {
    if (flesh < 0 || fiber < 0 || bone < 0)
      throw new IllegalArgumentException("Material costs must be nonnegative");
  }

  private Map<String, Integer> entries() {
    return Map.of(FLESH, flesh, FIBER, fiber, BONE, bone);
  }

  public boolean funded(ResourceAccount account) {
    return entries().entrySet().stream()
        .allMatch(e -> account.material(e.getKey()) >= e.getValue());
  }

  public boolean pay(ResourceAccount account) {
    if (!funded(account)) return false;
    var spent = new LinkedHashMap<String, Integer>();
    for (var entry : entries().entrySet())
      if (entry.getValue() > 0) {
        if (!account.spend(entry.getKey(), entry.getValue())) {
          spent.forEach(account::credit);
          return false;
        }
        spent.put(entry.getKey(), entry.getValue());
      }
    return true;
  }

  public void refund(ResourceAccount account) {
    entries()
        .forEach(
            (id, count) -> {
              if (count > 0) account.credit(id, count);
            });
  }

  public static boolean transfer(ResourceAccount from, ResourceAccount to, String id, int count) {
    if (count < 0) throw new IllegalArgumentException("Negative transfer");
    if (count == 0) return true;
    if (!from.spend(id, count)) return false;
    to.credit(id, count);
    return true;
  }
}
