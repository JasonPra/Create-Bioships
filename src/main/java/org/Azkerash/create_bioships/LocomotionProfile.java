package org.Azkerash.create_bioships;

/** Read-only movement capability contract; planners do not depend on a particular leg model. */
record LocomotionProfile(
    boolean walls,
    boolean ceilings,
    double gripReach,
    double comfortableClearance,
    int directDistance,
    int quickNodes,
    int searchNodes) {
  static final LocomotionProfile TENTACLES =
      new LocomotionProfile(true, true, 1.7, .7, 24, 220, 1600);

  LocomotionProfile {
    if (!Double.isFinite(gripReach)
        || gripReach < .6
        || gripReach > 8
        || !Double.isFinite(comfortableClearance)
        || comfortableClearance < .499
        || comfortableClearance > 2
        || directDistance < 2
        || directDistance > 64
        || quickNodes < 8
        || quickNodes > 4096
        || searchNodes < quickNodes
        || searchNodes > 8192)
      throw new IllegalArgumentException("Invalid locomotion profile limits");
  }
}
