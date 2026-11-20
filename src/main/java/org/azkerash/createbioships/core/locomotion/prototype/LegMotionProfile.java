package org.azkerash.createbioships.core.locomotion.prototype;

/** Leg-type capabilities, separate from the medium the creature currently occupies. */
public record LegMotionProfile(
    boolean adaptiveReach, boolean rearLeverage, boolean downhillTuck, GroundContact contact) {
  public enum GroundContact {
    FRICTION,
    ANCHORED
  }

  public static final LegMotionProfile GENERIC =
      new LegMotionProfile(false, false, false, GroundContact.FRICTION);
  public static final LegMotionProfile CRAB =
      new LegMotionProfile(true, true, true, GroundContact.FRICTION);
  // A future climbing-leg controller must implement anchored contacts itself; this profile grants
  // no adhesion.
}
