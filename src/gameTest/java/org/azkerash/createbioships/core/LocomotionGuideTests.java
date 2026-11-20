package org.azkerash.createbioships.core;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.azkerash.createbioships.CreateBioships;
import org.azkerash.createbioships.compat.letitwalk.*;
import org.azkerash.createbioships.core.brain.program.CerebralGuide;
import org.azkerash.createbioships.equipment.LocomotionGuideItem;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class LocomotionGuideTests {
  @GameTest(template = "test_empty", batch = "locomotion_manual")
  public static void field_manual_loads_and_matches_current_leg_defaults(GameTestHelper h)
      throws Exception {
    var stream =
        LocomotionGuideTests.class.getResourceAsStream(
            "/assets/create_bioships/" + LocomotionGuideItem.RESOURCE);
    h.assertTrue(stream != null, "Locomotion field guide resource missing");
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      var chapters = CerebralGuide.read(reader);
      var titles = new HashSet<String>();
      for (var chapter : chapters) {
        h.assertTrue(
            titles.add(chapter.title()) && !chapter.body().isBlank(),
            "Duplicate or empty manual chapter");
      }
      var all = chapters.toString();
      h.assertTrue(
          all.contains("1–" + CompactLegGeometry.MAX_HEIGHT)
              && all.contains("±" + SocketJointSettings.DEFAULT.bend() + "°")
              && all.contains(SocketJointSettings.DEFAULT.swivel() + "° swivel")
              && LimbTuning.DEFAULT.restAngle() == 0,
          "Guide settings have drifted from live leg controls");
      h.assertTrue(
          chapters.stream()
              .anyMatch(
                  c ->
                      c.example().contains("WEST side")
                          && c.example().contains("-45°")
                          && c.example().contains("+45°")),
          "Mirrored eight-leg setup missing");
    }
    h.assertTrue(
        CreateBioships.LOCOMOTION_CODEX.get().getDefaultInstance().getMaxStackSize() == 1,
        "Locomotion manual was not registered as a book item");
    h.succeed();
  }
}
