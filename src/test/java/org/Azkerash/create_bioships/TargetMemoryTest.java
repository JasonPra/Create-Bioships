package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TargetMemoryTest {
  @Test
  void expiresAtThreeRealSecondsRegardlessOfTicksOrQueries() {
    var nanos = new AtomicLong();
    var millis = new AtomicLong(10000);
    var memory = new TargetMemory<String>(nanos::get, millis::get);
    memory.observe("digester");
    nanos.set(2_999_999_999L);
    for (int i = 0; i < 10000; i++) assertTrue(memory.retains("digester"));
    nanos.incrementAndGet();
    assertFalse(memory.retains("digester"));
  }

  @Test
  void actualReobservationRefreshesDeadline() {
    var nanos = new AtomicLong();
    var memory = new TargetMemory<String>(nanos::get, () -> 0L);
    memory.observe("target");
    nanos.set(2_000_000_000L);
    memory.observe("target");
    nanos.set(4_999_999_999L);
    assertTrue(memory.retains("target"));
    nanos.incrementAndGet();
    assertFalse(memory.retains("target"));
  }

  @Test
  void selectionDoesNotMakeAnUnseenTargetImmortal() {
    var nanos = new AtomicLong();
    var memory = new TargetMemory<String>(nanos::get, () -> 0L);
    memory.observe("selected");
    memory.observe("other");
    nanos.set(3_000_000_000L);
    assertFalse(memory.retains("selected"));
    assertFalse(memory.retains("other"));
    memory.observe("other");
    assertTrue(memory.retains("other"));
    assertFalse(memory.retains("selected"));
  }

  @Test
  void reloadPreservesRemainingTimeWithoutRestartingTheTimeout() {
    var nanos = new AtomicLong();
    var millis = new AtomicLong(10000);
    var memory = new TargetMemory<String>(nanos::get, millis::get);
    memory.observe("target");
    long saved = memory.observedMillis("target");
    millis.set(11250);
    var restored = new TargetMemory<String>(nanos::get, millis::get);
    restored.restore("target", saved);
    nanos.set(1_749_999_999L);
    assertTrue(restored.retains("target"));
    nanos.incrementAndGet();
    assertFalse(restored.retains("target"));
  }

  @Test
  void expiredSavedObservationStaysExpired() {
    var memory = new TargetMemory<String>(() -> 0L, () -> 10000L);
    memory.restore("target", 7000);
    assertFalse(memory.retains("target"));
  }

  @Test
  void wallClockAdjustmentDoesNotChangeLiveElapsedTime() {
    var nanos = new AtomicLong();
    var millis = new AtomicLong(10000);
    var memory = new TargetMemory<String>(nanos::get, millis::get);
    memory.observe("target");
    millis.set(99999999);
    nanos.set(2_000_000_000L);
    assertTrue(memory.retains("target"));
    millis.set(0);
    nanos.set(3_000_000_000L);
    assertFalse(memory.retains("target"));
  }
}
