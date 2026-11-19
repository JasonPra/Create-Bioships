package org.Azkerash.create_bioships.development.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("create_bioships")
@PrefixGameTestTemplate(false)
public final class NativeLegReferenceTests {
  @GameTest(template = "test_empty", timeoutTicks = 400, batch = "upstream_leg_reference")
  public static void native_parts_stand_walk_and_share_connected_pivots(GameTestHelper h) {
    var base = new BlockPos(4864, 80, 128);
    h.runAfterDelay(
        20,
        () -> {
          var reference = NativeLegReference.create(h.getLevel(), base);
          h.runAfterDelay(
              100,
              () -> {
                h.assertTrue(
                    reference.walker().isPowered(),
                    "Native reference motor did not power the core");
                h.assertTrue(
                    NativeLegReference.gap(reference) < .08, "Upstream reference pivot gap");
                var start = reference.body().logicalPose().position().get(new org.joml.Vector3d());
                for (int delay = 0; delay <= 100; delay += 5)
                  h.runAfterDelay(
                      delay, () -> reference.walker().receiveInput(1, h.getLevel().getGameTime()));
                h.runAfterDelay(
                    110,
                    () -> {
                      h.assertTrue(
                          reference.body().logicalPose().position().distance(start) > .25,
                          "Upstream walker did not walk");
                      h.assertTrue(
                          NativeLegReference.gap(reference) < .08,
                          "Upstream walking pivots separated");
                      System.out.println(
                          "NATIVE_REFERENCE_PASS limbs=4 moving=true gap="
                              + NativeLegReference.gap(reference));
                      h.succeed();
                    });
              });
        });
  }
}
