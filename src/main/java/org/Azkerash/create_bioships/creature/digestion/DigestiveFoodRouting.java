package org.Azkerash.create_bioships.creature.digestion;

import java.util.*;

/**
 * Bounded queue balancing. Inventory owners perform atomic, provenance-preserving meal transfers.
 */
public final class DigestiveFoodRouting {
  public interface Port<T> {
    int queuedFood();

    boolean moveMealTo(T recipient);
  }

  /** At most one meal is transferred per recipient per pass. Never transfers across groups. */
  public static <T extends Port<T>> int balance(List<T> members) {
    var recipients = new ArrayList<>(members);
    recipients.sort(Comparator.comparingInt(Port::queuedFood));
    int moved = 0;
    for (var recipient : recipients) {
      var donors = new ArrayList<>(members);
      donors.sort(Comparator.comparingInt((T port) -> port.queuedFood()).reversed());
      for (var donor : donors)
        if (donor != recipient
            && donor.queuedFood() > recipient.queuedFood()
            && donor.moveMealTo(recipient)) {
          moved++;
          break;
        }
    }
    return moved;
  }

  private DigestiveFoodRouting() {}
}
