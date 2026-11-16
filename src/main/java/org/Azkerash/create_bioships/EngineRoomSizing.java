package org.Azkerash.create_bioships;

/** Pure room-capacity policy: each added pair of side bays widens the engine room. */
final class EngineRoomSizing {
  static int columns(int digestivePairs) {
    return Math.max(1, (digestivePairs + 1) / 2);
  }

  static int capacity(int columns) {
    return 2 * Math.max(1, columns);
  }

  static int widening(int columns) {
    return 2 * (Math.max(1, columns) - 1);
  }

  static int width(int columns) {
    return 9 + 2 * widening(columns);
  }
}
