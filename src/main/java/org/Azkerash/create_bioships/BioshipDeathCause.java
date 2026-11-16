package org.Azkerash.create_bioships;

/** Persisted fatal event; old records without evidence remain unknown. */
public enum BioshipDeathCause {
  NONE,
  UNKNOWN,
  STARVATION,
  DROWNING,
  ASPHYXIATION,
  CORE_REMOVED,
  COMBAT,
  EXPLOSION,
  INJURY;

  static BioshipDeathCause read(String value) {
    try {
      var cause = valueOf(value);
      return cause == NONE ? UNKNOWN : cause;
    } catch (IllegalArgumentException ignored) {
      return UNKNOWN;
    }
  }
}
