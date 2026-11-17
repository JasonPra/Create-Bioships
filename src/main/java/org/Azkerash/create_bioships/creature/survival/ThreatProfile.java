package org.Azkerash.create_bioships.creature.survival;

/** Capabilities can be combined: a trident drowned is both ranged and aquatic. */
public record ThreatProfile(
    boolean explosive,
    boolean ranged,
    boolean climbing,
    boolean flying,
    boolean aquatic,
    boolean jumping) {
  public static final ThreatProfile GROUND =
      new ThreatProfile(false, false, false, false, false, false);
}
