package org.azkerash.createbioships.client.components;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** Local UI preferences only: never stored on a card or synchronized with the server. */
final class CerebralIdeLayout {
  private static final double DEFAULT_DIAGNOSTIC_FRACTION = .16;
  private static final double MIN_FRACTION = .05;
  private static final double MAX_FRACTION = .75;
  private static final Path FILE =
      FMLPaths.CONFIGDIR.get().resolve("create_bioships-ide.properties");
  private double diagnosticFraction = DEFAULT_DIAGNOSTIC_FRACTION;
  private boolean diagnosticsHidden;

  static CerebralIdeLayout load() {
    var layout = new CerebralIdeLayout();
    if (Files.isRegularFile(FILE)) {
      try (var reader = Files.newBufferedReader(FILE)) {
        var values = new Properties();
        values.load(reader);
        layout.setDiagnosticFraction(
            Double.parseDouble(
                values.getProperty(
                    "diagnosticFraction", Double.toString(DEFAULT_DIAGNOSTIC_FRACTION))));
        layout.diagnosticsHidden =
            Boolean.parseBoolean(values.getProperty("diagnosticsHidden", "false"));
      } catch (IOException | IllegalArgumentException e) {
        LogUtils.getLogger().warn("Could not read Cerebral IDE layout; using defaults", e);
      }
    }
    return layout;
  }

  double getDiagnosticFraction() {
    return diagnosticFraction;
  }

  boolean isDiagnosticsHidden() {
    return diagnosticsHidden;
  }

  void setDiagnosticsHidden(boolean hidden) {
    diagnosticsHidden = hidden;
  }

  void setDiagnosticFraction(double fraction) {
    diagnosticFraction =
        Double.isFinite(fraction)
            ? Math.max(MIN_FRACTION, Math.min(MAX_FRACTION, fraction))
            : DEFAULT_DIAGNOSTIC_FRACTION;
  }

  void save() {
    var values = new Properties();
    values.setProperty("diagnosticFraction", Double.toString(diagnosticFraction));
    values.setProperty("diagnosticsHidden", Boolean.toString(diagnosticsHidden));
    try {
      Files.createDirectories(FILE.getParent());
      try (var writer = Files.newBufferedWriter(FILE)) {
        values.store(writer, "Cerebral IDE client layout");
      }
    } catch (IOException e) {
      LogUtils.getLogger().warn("Could not save Cerebral IDE layout", e);
    }
  }
}
