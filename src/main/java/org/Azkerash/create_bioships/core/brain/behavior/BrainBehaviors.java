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
import org.Azkerash.create_bioships.core.brain.program.ProgramBehavior;
import org.Azkerash.create_bioships.core.brain.program.ProgramCardData;

/**
 * Factory registration and one active program per brain. Future cards register their own
 * constructors.
 */
@EventBusSubscriber(modid = "create_bioships")
public final class BrainBehaviors {
  public static final ResourceLocation PROGRAM_ID =
      ResourceLocation.parse("create_bioships:cerebral_program");

  private record ProgramKey(
      int slot,
      java.util.UUID identity,
      String fingerprint,
      net.minecraft.world.item.ItemStack card) {}

  private record Active(ResourceLocation id, Object key, BrainBehavior behavior) {}

  public static ProgramBehavior.View programView(
      ServerSubLevel body, net.minecraft.world.item.ItemStack card) {
    var active = ACTIVE.get(SubLevelCreatureParts.root(body));
    if (active != null
        && active.key instanceof ProgramKey key
        && key.card() == card
        && ProgramCardData.executable(card)
        && key.fingerprint().equals(ProgramCardData.document(card).getString("CompiledHash"))
        && active.behavior instanceof ProgramBehavior program) {
      return program.view();
    }
    return new ProgramBehavior.View(
        "Not running: select a compiled card in active brain memory.", java.util.List.of());
  }

  private static final Map<ResourceLocation, Supplier<? extends BrainBehavior>> FACTORIES =
      new HashMap<>();
  private static final Map<ServerSubLevel, Active> ACTIVE = new IdentityHashMap<>();

  static {
    register(WanderBehavior.ID, WanderBehavior::new);
  }

  private BrainBehaviors() {}

  public static void register(ResourceLocation id, Supplier<? extends BrainBehavior> factory) {
    if (FACTORIES.putIfAbsent(id, factory) != null) {
      throw new IllegalArgumentException("Duplicate brain behavior: " + id);
    }
  }

  public static ResourceLocation active(ServerSubLevel body) {
    var value = ACTIVE.get(SubLevelCreatureParts.root(body));
    return value == null ? null : value.id;
  }

  public static void discard(ServerSubLevel body) {
    var previous = ACTIVE.remove(body);
    if (previous != null) {
      previous.behavior.stop(body);
    }
  }

  public static void advance(ServerSubLevel body) {
    if (SubLevelCreatureParts.isPart(body)) {
      return;
    }
    ResourceLocation selected = null;
    Object key = null;
    String source = null;
    if (!body.isRemoved() && SubLevelData.isLiving(body) && !SubLevelHealth.status(body).dead()) {
      var brain = SubLevelBrains.get(body);
      for (int i = 0; i < brain.capacity(); i++) {
        var stack = brain.inventory().getItem(i);
        if (ProgramCardData.executable(stack)) {
          selected = PROGRAM_ID;
          source = ProgramCardData.source(stack);
          key =
              new ProgramKey(
                  i,
                  ProgramCardData.identity(stack),
                  ProgramCardData.document(stack).getString("CompiledHash"),
                  stack);
          break;
        }
        if (stack.getItem() instanceof FactoryBehaviorCardItem card
            && FACTORIES.containsKey(card.behavior())) {
          selected = card.behavior();
          key = selected;
          break;
        }
      }
    }
    var previous = ACTIVE.get(body);
    if (previous != null && !java.util.Objects.equals(previous.key, key)) {
      previous.behavior.stop(body);
      ACTIVE.remove(body);
      previous = null;
    }
    if (selected == null) {
      return;
    }
    if (previous == null) {
      BrainBehavior behavior =
          selected.equals(PROGRAM_ID) ? new ProgramBehavior(source) : FACTORIES.get(selected).get();
      previous = new Active(selected, key, behavior);
      ACTIVE.put(body, previous);
    }
    previous.behavior.tick(body);
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    for (var body : java.util.List.copyOf(ACTIVE.keySet())) {
      if (body.isRemoved() || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead()) {
        discard(body);
      }
    }
    if (event.getServer().getTickCount() % 10 != 0) {
      return;
    }
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container == null) {
        continue;
      }
      for (var entry : container.getAllSubLevels()) {
        if (entry instanceof ServerSubLevel body && SubLevelData.isLiving(body)) {
          advance(body);
        }
      }
    }
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    ACTIVE.clear();
  }
}
