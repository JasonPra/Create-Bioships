package org.Azkerash.create_bioships.creature.digestion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DigestiveFoodRoutingTest {
  static final class Queue implements DigestiveFoodRouting.Port<Queue> {
    int count;
    final int capacity;

    Queue(int count, int capacity) {
      this.count = count;
      this.capacity = capacity;
    }

    public int queuedFood() {
      return count;
    }

    public boolean moveMealTo(Queue target) {
      if (count - target.count < 2 || target.count == target.capacity) return false;
      count--;
      target.count++;
      return true;
    }
  }

  @Test
  void balancesWithoutDuplicatingOrOscillatingMeals() {
    var a = new Queue(9, 9);
    var b = new Queue(0, 9);
    var c = new Queue(0, 9);
    for (int i = 0; i < 20; i++) DigestiveFoodRouting.balance(List.of(a, b, c));
    assertEquals(9, a.count + b.count + c.count);
    assertEquals(3, a.count);
    assertEquals(3, b.count);
    assertEquals(3, c.count);
    assertEquals(0, DigestiveFoodRouting.balance(List.of(a, b, c)));
  }

  @Test
  void rejectedTransferDoesNotLoseFood() {
    var a = new Queue(9, 9);
    var full = new Queue(0, 0);
    assertEquals(0, DigestiveFoodRouting.balance(List.of(a, full)));
    assertEquals(9, a.count);
    assertEquals(0, full.count);
  }
}
