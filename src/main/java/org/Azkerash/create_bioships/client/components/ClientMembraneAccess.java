package org.Azkerash.create_bioships.client.components;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.compat.biomancy.network.MembraneAccessPayload;

/** Read-only collision prediction. The server owns and validates all permissions. */
@EventBusSubscriber(modid = "create_bioships", value = Dist.CLIENT)
public final class ClientMembraneAccess {
  private static final Map<UUID, Set<UUID>> ACCESS = new HashMap<>();
  private static net.minecraft.client.multiplayer.ClientLevel world;

  private ClientMembraneAccess() {}

  public static void receive(MembraneAccessPayload p) {
    var current = Minecraft.getInstance().level;
    if (current != world) {
      ACCESS.clear();
      world = current;
    }
    if (p.mode() != 2) ACCESS.remove(p.grid());
    else ACCESS.put(p.grid(), Set.copyOf(p.allowed()));
  }

  public static Boolean permits(UUID body, UUID entity) {
    var allowed = ACCESS.get(body);
    return allowed == null ? null : allowed.contains(entity);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
    var current = Minecraft.getInstance().level;
    if (current != world) {
      ACCESS.clear();
      world = current;
    }
  }
}
