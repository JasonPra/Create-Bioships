package org.Azkerash.create_bioships.core;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.combat.CombatAdapter;

/** Temporary fixed-rate metabolism; saved state belongs to SubLevelData, not the timing policy. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class SubLevelHunger {
  public static final int DRAIN_INTERVAL = 20 * 60,
      STARVATION_INTERVAL = 20 * 4,
      REGENERATION_INTERVAL = 20 * 4;

  private static final java.util.Map<ServerSubLevel, java.util.Set<Long>> STOMACHS =
      new java.util.WeakHashMap<>();

  static void initializeCapacity(ServerSubLevel body, java.util.Collection<Long> cells) {
    var stomachs = new java.util.HashSet<Long>();
    for (long cell : cells)
      if (body.getLevel()
          .getBlockState(net.minecraft.core.BlockPos.of(cell))
          .is(org.Azkerash.create_bioships.components.ComponentRegistry.STOMACH.get()))
        stomachs.add(cell);
    STOMACHS.put(body, stomachs);
    capacityChanged(body, stomachs);
  }

  static void blockChanged(
      ServerSubLevel body,
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.level.block.state.BlockState state) {
    var stomachs = STOMACHS.get(body);
    if (stomachs == null) return;
    boolean changed =
        state.is(org.Azkerash.create_bioships.components.ComponentRegistry.STOMACH.get())
            ? stomachs.add(pos.asLong())
            : stomachs.remove(pos.asLong());
    if (changed) capacityChanged(body, stomachs);
  }

  private static void capacityChanged(ServerSubLevel body, java.util.Set<Long> stomachs) {
    int capacity = 0;
    for (long cell : stomachs) {
      var be = body.getLevel().getBlockEntity(net.minecraft.core.BlockPos.of(cell));
      int bonus =
          be
                  instanceof
                  org.Azkerash.create_bioships.components.logic.stomach.StomachBlockEntity stomach
              ? stomach.genes().capacityBonus()
              : 0;
      capacity = Math.max(capacity, SubLevelData.STOMACH_HUNGER + bonus);
    }
    if (SubLevelCreatureParts.isPart(body)) {
      SubLevelCreatureParts.stomachContribution(body, capacity);
      return;
    }
    capacity = Math.max(capacity, SubLevelCreatureParts.extraStomachCapacity(body));
    SubLevelData.maximumHunger(body, SubLevelData.BASE_HUNGER + capacity);
  }

  private static final java.util.Set<ServerSubLevel> PENDING_CAPACITY =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

  public static void refreshCapacity(ServerSubLevel body) {
    if (!SubLevelHealth.initialized(body)) {
      PENDING_CAPACITY.add(body);
      return;
    }
    SubLevelHealth.status(body);
    var stomachs = STOMACHS.get(body);
    if (stomachs != null) capacityChanged(body, stomachs);
  }

  public static boolean hasStomach(ServerSubLevel body) {
    body = SubLevelCreatureParts.root(body);
    SubLevelHealth.status(body);
    return !STOMACHS.getOrDefault(body, java.util.Set.of()).isEmpty()
        || SubLevelCreatureParts.extraStomachCapacity(body) > 0;
  }

  public static int minimumHungerToHeal(ServerSubLevel body) {
    return (SubLevelData.maximumHunger(body) * 9 + 9) / 10;
  }

  public static boolean canRegenerate(ServerSubLevel body) {
    return !SubLevelHealth.status(body).dead()
        && SubLevelData.hunger(body) >= minimumHungerToHeal(body);
  }

  private SubLevelHunger() {}

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    var pending = java.util.List.copyOf(PENDING_CAPACITY);
    PENDING_CAPACITY.clear();
    for (var body : pending)
      if (!body.isRemoved()) {
        SubLevelHealth.status(body);
        refreshCapacity(body);
      }
    for (var level : event.getServer().getAllLevels()) {
      if (level.getGameTime() % 20 != 0) continue;
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body && CoreAssemblyRules.isCreature(body))
          advance(body, 20);
    }
  }

  /** Advances loaded simulation time only; paused/unloaded creatures accrue no catch-up debt. */
  public static void advance(ServerSubLevel body, int ticks) {
    if (ticks <= 0 || body.isRemoved() || SubLevelCreatureParts.isPart(body)) return;
    var state = SubLevelData.hungerState(body);
    if (state.paused() || SubLevelHealth.status(body).dead()) return;
    int value = state.value();
    int drain = Math.clamp(state.drainTicks(), 0, DRAIN_INTERVAL - 1);
    int starvation = Math.clamp(state.starvationTicks(), 0, STARVATION_INTERVAL - 1);
    int regeneration =
        Math.clamp(SubLevelData.regenerationTicks(body), 0, REGENERATION_INTERVAL - 1);
    while (ticks > 0 && !SubLevelHealth.status(body).dead()) {
      int step = Math.min(20, ticks);
      ticks -= step;
      if (value > 0) {
        drain += step;
        starvation = 0;
        if (drain >= DRAIN_INTERVAL) {
          value--;
          drain -= DRAIN_INTERVAL;
        }
      } else {
        starvation += step;
        if (starvation >= STARVATION_INTERVAL) {
          starvation -= STARVATION_INTERVAL;
          var target = CombatAdapter.target(body);
          if (target != null) {
            var before = SubLevelHealth.status(body);
            target.hurt(body.getLevel().damageSources().starve(), 1);
            org.Azkerash.create_bioships.core.trust.SubLevelTrustLoss.starvation(body, before);
          }
        }
      }
      var health = SubLevelHealth.status(body);
      if (!health.dead()
          && value >= minimumHungerToHeal(body)
          && health.current() < health.maximum()) {
        regeneration += step;
        if (regeneration >= REGENERATION_INTERVAL) {
          regeneration -= REGENERATION_INTERVAL;
          var target = CombatAdapter.target(body);
          if (target != null) target.heal(1);
        }
      } else regeneration = 0;
    }
    SubLevelData.regenerationTicks(body, regeneration);
    SubLevelData.hungerState(body, new SubLevelData.Hunger(value, false, drain, starvation));
  }
}
