package org.Azkerash.create_bioships;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Observation freshness uses real elapsed time, independently of server tick rate. */
final class TargetMemory<K> {
  static final long TIMEOUT = TimeUnit.SECONDS.toNanos(3);

  private record Seen(long nanos, long epochMillis) {}

  private final Map<K, Seen> seen = new HashMap<>();
  private final LongSupplier nanos, millis;

  TargetMemory() {
    this(System::nanoTime, System::currentTimeMillis);
  }

  TargetMemory(LongSupplier nanos, LongSupplier millis) {
    this.nanos = nanos;
    this.millis = millis;
  }

  void observe(K key) {
    seen.put(key, new Seen(nanos.getAsLong(), millis.getAsLong()));
  }

  boolean retains(K key) {
    return seen.containsKey(key) && age(key) < TIMEOUT;
  }

  long age(K key) {
    var value = seen.get(key);
    return value == null ? TIMEOUT : Math.max(0, nanos.getAsLong() - value.nanos);
  }

  long observedMillis(K key) {
    var value = seen.get(key);
    return value == null ? millis.getAsLong() : value.epochMillis;
  }

  void restore(K key, long observedMillis) {
    long ageMillis = Math.min(3000, Math.max(0, millis.getAsLong() - observedMillis));
    seen.put(
        key,
        new Seen(nanos.getAsLong() - TimeUnit.MILLISECONDS.toNanos(ageMillis), observedMillis));
  }

  void forget(K key) {
    seen.remove(key);
  }

  void clear() {
    seen.clear();
  }
}
