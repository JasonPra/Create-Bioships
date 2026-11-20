package org.Azkerash.create_bioships.client.components;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.core.brain.program.editor.EditorLog;

/** Private, bounded per-card client-session histories. No source or history broadcasts. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class CerebralDiagnosticLogs {
  private static final LinkedHashMap<UUID, EditorLog> LOGS = new LinkedHashMap<>(32, .75f, true);
  private static Object world;

  private CerebralDiagnosticLogs() {}

  static EditorLog get(UUID identity) {
    if (world != Minecraft.getInstance().level) {
      LOGS.clear();
      world = Minecraft.getInstance().level;
    }
    var log = LOGS.computeIfAbsent(identity, id -> new EditorLog());
    while (LOGS.size() > 32) {
      LOGS.remove(LOGS.keySet().iterator().next());
    }
    return log;
  }

  @SubscribeEvent
  public static void logout(
      net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
    LOGS.clear();
    world = null;
  }
}
