package org.azkerash.createbioships.core.brain.program;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Resource-backed documentation shared by the client reader and example validation checks. */
public final class CerebralGuide {
  public static final String RESOURCE = "guide/cerebral.json";

  public record Chapter(String title, String body, String example) {}

  private CerebralGuide() {}

  public static List<Chapter> read(Reader reader) {
    var chapters = new ArrayList<Chapter>();
    for (var entry : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("chapters")) {
      var chapter = entry.getAsJsonObject();
      chapters.add(
          new Chapter(
              chapter.get("title").getAsString(),
              chapter.get("body").getAsString(),
              chapter.has("example") ? chapter.get("example").getAsString() : ""));
    }
    if (chapters.isEmpty()) {
      throw new IllegalArgumentException("Scripting guide has no chapters");
    }
    return List.copyOf(chapters);
  }
}
