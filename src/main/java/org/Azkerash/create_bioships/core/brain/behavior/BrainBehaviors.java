package org.Azkerash.create_bioships.core.brain.behavior;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.Azkerash.create_bioships.components.logic.brain.FactoryBehaviorCardItem;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.core.brain.SubLevelBrains;

/**
 * Factory registration and one active program per brain. Future cards register their own
 * constructors.
 */
@EventBusSubscriber(modid = "create_bioships")
public final class BrainBehaviors {
  private record Active(ResourceLocation id, BrainBehavior behavior) {}

  private static final Map<ResourceLocation, Supplier<? extends BrainBehavior>> FACTORIES =
      new HashMap<>();
  private static final Map<ServerSubLevel, Active> ACTIVE = new IdentityHashMap<>();

  static {
    register(WanderBehavior.ID, WanderBehavior::new);
  }

  private BrainBehaviors() {}

  public static void register(ResourceLocation id, Supplier<? extends BrainBehavior> factory) {
    if (FACTORIES.putIfAbsent(id, factory) != null)
      throw new IllegalArgumentException("Duplicate brain behavior: " + id);
  }

  public static ResourceLocation active(ServerSubLevel body) {
    var value = ACTIVE.get(body);
    return value == null ? null : value.id;
  }

  public static void advance(ServerSubLevel body) {
    ResourceLocation selected = null;
    if (!body.isRemoved() && SubLevelData.isLiving(body) && !SubLevelHealth.status(body).dead()) {
      var brain = SubLevelBrains.get(body);
      for (int i = 0; i < brain.capacity(); i++)
        if (brain.inventory().getItem(i).getItem() instanceof FactoryBehaviorCardItem card
            && FACTORIES.containsKey(card.behavior())) {
          selected = card.behavior();
          break;
        }
    }
    var previous = ACTIVE.get(body);
    if (previous != null && !previous.id.equals(selected)) {
      previous.behavior.stop(body);
      ACTIVE.remove(body);
      previous = null;
    }
    if (selected == null) return;
    if (previous == null) {
      previous = new Active(selected, FACTORIES.get(selected).get());
      ACTIVE.put(body, previous);
    }
    previous.behavior.tick(body);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    ACTIVE.keySet().removeIf(ServerSubLevel::isRemoved);
    if (event.getServer().getTickCount() % 10 != 0) return;
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body && SubLevelData.isLiving(body)) advance(body);
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    ACTIVE.clear();
  }
}
