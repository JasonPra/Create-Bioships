package org.Azkerash.create_bioships.core.brain.program;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.behavior.BrainBehavior;

/** One ephemeral machine per selected physical program card. */
public final class ProgramBehavior implements BrainBehavior {
  public record View(String state, List<String> output) {
    public View {
      output = List.copyOf(output);
    }
  }

  private final ProgramMachine machine;
  private final ProgramDiagnostic compileError;

  public ProgramBehavior(String source) {
    // Always compile server-side; persisted flags are never trusted as executable code.
    var compiled = ProgramCompiler.compile(source);
    machine = compiled.success() ? new ProgramMachine(compiled.program()) : null;
    compileError = compiled.diagnostic();
  }

  public void tick(ServerSubLevel body) {
    if (machine == null) {
      return;
    }
    machine.advance(
        body.getLevel().getGameTime(),
        name ->
            switch (name) {
              case "Health" -> SubLevelHealth.status(body).current();
              case "Hunger" -> SubLevelData.hunger(body);
              case "Oxygen" -> SubLevelOxygen.status(body).current();
              case "Tick" -> body.getLevel().getGameTime();
              default -> throw new IllegalArgumentException("Unknown statistic");
            });
  }

  public View view() {
    if (machine == null) {
      return new View("Compile error: " + compileError, List.of());
    }
    String state = machine.state().name();
    if (machine.fault() != null) {
      state += ": " + machine.fault();
    }
    return new View(state, machine.output());
  }

  public void stop(ServerSubLevel body) {
    if (machine != null) {
      machine.stop();
    }
    BrainBehavior.super.stop(body);
  }
}
