package org.Azkerash.create_bioships.creature.respiration;

/** Finite air and recovery hysteresis, independent of anatomy, navigation and damage effects. */
public final class AirSupply {
  public static final int CAPACITY = 300, SEEK_BELOW = 200;
  private int remaining = CAPACITY;
  private boolean seeking;

  public int remaining() {
    return remaining;
  }

  public boolean seeking() {
    return seeking;
  }

  public boolean needsAir(boolean breathing, boolean airAccess) {
    return seeking && (breathing || !airAccess);
  }

  public void tick(boolean breathing, boolean airAccess, boolean workingGills) {
    remaining = breathing ? Math.min(CAPACITY, remaining + 4) : Math.max(0, remaining - 1);
    if (workingGills || remaining == CAPACITY) seeking = false;
    else if (!airAccess && !breathing && remaining < SEEK_BELOW) seeking = true;
  }

  public void restore(int air, boolean seeking) {
    remaining = Math.clamp(air, 0, CAPACITY);
    this.seeking = seeking && remaining < CAPACITY;
  }
}
