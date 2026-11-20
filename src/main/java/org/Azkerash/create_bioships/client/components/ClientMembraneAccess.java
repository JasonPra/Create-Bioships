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
    if (p.mode() != 2) {
      ACCESS.remove(p.grid());
    } else {
      ACCESS.put(p.grid(), Set.copyOf(p.allowed()));
    }
  }

  public static Boolean permits(UUID body, UUID entity) {
    var allowed = ACCESS.get(body);
    return allowed == null ? null : allowed.contains(entity);
  }

  public static Boolean permits(UUID host, net.minecraft.world.entity.Entity entity) {
    var allowed = ACCESS.get(host);
    if (allowed == null) {
      return null;
    }
    if (allowed.contains(entity.getUUID())) {
      return true;
    }
    if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) {
      return false;
    }
    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(entity.level());
    if (container == null) {
      return false;
    }
    for (var id : allowed) {
      var found = container.getSubLevel(id);
      if (found instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel craft
          && org.Azkerash.create_bioships.compat.biomancy.ConstructMembranePassage.within(
              craft, entity, craft.renderPose())) {
        return true;
      }
    }
    return false;
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
