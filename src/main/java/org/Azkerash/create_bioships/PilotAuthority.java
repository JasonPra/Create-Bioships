package org.Azkerash.create_bioships;

/** High trust permits deliberate piloting to override the instinct to retreat. */
final class PilotAuthority {
  static final int TRUST_THRESHOLD = 80;

  private PilotAuthority() {}

  static boolean obeys(
      int trust, boolean authorized, boolean present, boolean aboard, long now, long commandUntil) {
    return trust >= TRUST_THRESHOLD && authorized && present && (aboard || now < commandUntil);
  }
}
