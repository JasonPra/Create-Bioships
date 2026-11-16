package org.Azkerash.create_bioships;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BioshipVoicePaletteTest {
  @Test
  void allLettersHaveDistinctBoundedArticulationsAndLowercaseMatches() {
    var unique = new HashSet<BioshipVoicePalette.Profile>();
    for (char c = 'A'; c <= 'Z'; c++) {
      var voice = BioshipVoicePalette.letter(c);
      assertTrue(voice.pitch() >= .5f && voice.pitch() <= 2);
      assertTrue(voice.ticks() > 0 && voice.ticks() <= 20);
      assertTrue(voice.volume() > 0 && voice.volume() <= 1);
      assertEquals(voice, BioshipVoicePalette.letter(Character.toLowerCase(c)));
      unique.add(voice);
    }
    assertEquals(26, unique.size());
  }

  @Test
  void digitsPunctuationAndNonAsciiAreExcluded() {
    for (char c : new char[] {'0', '9', '!', ' ', '\u00e9', '\u00df'})
      assertThrows(IllegalArgumentException.class, () -> BioshipVoicePalette.letter(c));
  }
}
