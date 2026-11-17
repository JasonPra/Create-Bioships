package org.Azkerash.create_bioships;

import java.util.regex.Pattern;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/** Optional development-only selection; absent from the distributed mod and default full runs. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class GameTestSelection {
  @SubscribeEvent
  public static void select(FMLLoadCompleteEvent event) {
    String expression = System.getProperty("bioships.gameTests", "");
    if (expression.isBlank() || !GameTestHooks.isGametestServer()) return;
    var pattern = Pattern.compile(expression);
    event.enqueueWork(
        () -> {
          GameTestHooks.registerGametests();
          var tests = GameTestRegistry.getAllTestFunctions();
          int total = tests.size();
          tests.removeIf(test -> !pattern.matcher(test.testName()).find());
          if (tests.isEmpty())
            throw new IllegalArgumentException("No GameTests match: " + expression);
          System.out.println(
              "BIOSHIP_TEST_SELECTION selected="
                  + tests.size()
                  + "/"
                  + total
                  + " pattern="
                  + expression);
        });
  }
}
