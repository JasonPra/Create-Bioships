package org.Azkerash.create_bioships;

import java.util.*;
import java.util.function.Function;

/** A bounded ranked snapshot: eligibility belongs to the proposer, execution to the consumer. */
final class GoalQueue<T> {
  private List<T> pending = List.of();

  List<T> pending() {
    return pending;
  }

  void refresh(
      Collection<T> candidates, Function<T, BehaviorPriority.Offer> priority, String previous) {
    var order = BehaviorPriority.order(previous);
    var unique = new LinkedHashMap<String, T>();
    candidates.stream()
        .sorted((a, b) -> order.compare(priority.apply(b), priority.apply(a)))
        .forEach(candidate -> unique.putIfAbsent(priority.apply(candidate).key(), candidate));
    pending = unique.values().stream().limit(256).toList();
  }
}
