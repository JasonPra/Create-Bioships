package org.azkerash.createbioships.core.brain.program;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.azkerash.createbioships.core.brain.program.editor.*;

/** Documentation examples must remain valid against the actual sandbox compiler and machine. */
public final class CerebralGuideChecks {
  public static void main(String[] args) throws Exception {
    run();
    System.out.println("CEREBRAL_GUIDE_CHECKS_PASS");
  }

  public static void run() throws Exception {
    String path = "/assets/create_bioships/" + CerebralGuide.RESOURCE;
    var stream = CerebralGuideChecks.class.getResourceAsStream(path);
    if (stream == null) {
      throw new AssertionError("Guide resource is missing");
    }
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      var chapters = CerebralGuide.read(reader);
      int examples = 0;
      String all = chapters.toString();
      for (var chapter : chapters) {
        if (chapter.example().isEmpty()) {
          continue;
        }
        examples++;
        var compiled = ProgramCompiler.compile(chapter.example());
        if (!compiled.success()) {
          throw new AssertionError(chapter.title() + ": " + compiled.diagnostic());
        }
        var machine = new ProgramMachine(compiled.program());
        machine.advance(0, name -> 10);
        if (machine.state() == ProgramMachine.State.FAULTED) {
          throw new AssertionError(chapter.title() + ": " + machine.fault());
        }
      }
      if (examples == 0) {
        throw new AssertionError("Guide has no runnable examples");
      }
      for (String owner : java.util.List.of("Brain", "Console")) {
        for (var suggestion :
            SourceCompletion.suggest(new SourceDocument(owner + "."), true).suggestions()) {
          String name = suggestion.text().replace("()", "");
          if (!all.contains(owner + "." + name)) {
            throw new AssertionError("Guide omits supported API " + owner + "." + name);
          }
        }
      }
    }
  }
}
