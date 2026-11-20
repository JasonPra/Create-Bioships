package org.Azkerash.create_bioships.development.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class EstateLandscapeTests {
  @GameTest(template = "test_empty", batch = "estate_landscape", timeoutTicks = 20)
  public static void landscape_preserves_vault_and_roads_and_has_substantial_relief(
      GameTestHelper h) {
    int peak = 0;
    for (int x = -200; x <= 200; x++)
      for (int z = -200; z <= 200; z++) {
        int y = EstateLandscape.height(x, z);
        peak = Math.max(peak, y);
        h.assertTrue(y >= 0 && y <= 32, "Terrain escaped intended vertical bounds");
        if (EstateLandscape.central(x, z) || EstateLandscape.protectedRoad(x, z))
          h.assertTrue(y == 0, "Landscape buries architecture or road");
        if (!EstateLandscape.within(x, z))
          h.assertTrue(y == 0, "Terrain fails to taper at outer boundary");
      }
    h.assertTrue(peak >= 20, "Landscape is still effectively flat");
    h.succeed();
  }
}
