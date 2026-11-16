package org.Azkerash.create_bioships;

/** Pre-speech articulations, not spoken letter names or text-to-speech. ASCII A-Z only. */
public final class BioshipVoicePalette {
  public record Profile(float pitch, int ticks, float volume, int attack, boolean breathy) {}

  private static final float[] PITCH = {
    .94f, .72f, 1.31f, .79f, 1.18f, 1.39f, .67f, .88f, 1.42f, .83f, .75f, 1.02f, .63f, .71f, .81f,
    1.12f, .69f, .59f, 1.53f, .91f, .74f, 1.24f, .86f, 1.46f, 1.08f, 1.34f
  };

  public static Profile letter(char value) {
    char c = value >= 'a' && value <= 'z' ? (char) (value - 'a' + 'A') : value;
    if (c < 'A' || c > 'Z') throw new IllegalArgumentException("Voice palette accepts A-Z only");
    boolean vowel = "AEIOUY".indexOf(c) >= 0;
    boolean breathy = "CFHSXZ".indexOf(c) >= 0;
    int ticks = vowel ? 15 + c % 4 : "LMNRVW".indexOf(c) >= 0 ? 11 + c % 3 : 5 + c % 4;
    return new Profile(PITCH[c - 'A'], ticks, vowel ? .5f : .4f, vowel ? 3 : 1, breathy);
  }
}
