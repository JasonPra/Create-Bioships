package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.registry.Create_bioships;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persistent learned survival traits; each damaging explosion is credited only after survival. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID)
public final class BioshipSurvivalAdaptations {
  static final int MAX_BLAST_POINTS = 100, MEAT_POINTS = 1, SURVIVAL_POINTS = 3;
  private int blastPoints;

  private record Subject(ServerLevel level, UUID ship) {}

  private static final Map<Explosion, Set<Subject>> PENDING = new IdentityHashMap<>();
  private static final ThreadLocal<Deque<Explosion>> ACTIVE =
      ThreadLocal.withInitial(ArrayDeque::new);

  public int blastPoints() {
    return blastPoints;
  }

  void ateCreeperMeat(int count) {
    blastPoints =
        (int) Math.min(MAX_BLAST_POINTS, blastPoints + (long) Math.max(0, count) * MEAT_POINTS);
  }

  void survivedExplosion() {
    blastPoints = Math.min(MAX_BLAST_POINTS, blastPoints + SURVIVAL_POINTS);
  }

  public float blockResistance(float base) {
    return base + blastPoints * .25f;
  }

  float damage(float amount) {
    return amount * (1 - blastPoints * .005f);
  }

  public static void begin(Explosion explosion) {
    ACTIVE.get().push(explosion);
  }

  public static void end() {
    if (!ACTIVE.get().isEmpty()) ACTIVE.get().pop();
  }

  static void damaged(LivingCoreBlockEntity core) {
    var active = ACTIVE.get();
    if (active.isEmpty() || core.ship() == null) return;
    PENDING
        .computeIfAbsent(active.peek(), x -> new HashSet<>())
        .add(new Subject((ServerLevel) core.getLevel(), core.ship().getUniqueId()));
  }

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
  public static void settle(ServerTickEvent.Post event) {
    for (var subjects : PENDING.values())
      for (var subject : subjects) {
        if (subject.level().getServer() != event.getServer()) continue;
        var ship =
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(subject.level())
                .getSubLevel(subject.ship());
        if (!(ship instanceof ServerSubLevel owner) || owner.isRemoved()) continue;
        for (var actor : BioshipIntegration.blockEntities(owner))
          if (actor instanceof LivingCoreBlockEntity core && !core.body().dead()) {
            core.survival().survivedExplosion();
            core.setChanged();
          }
      }
    PENDING.clear();
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent event) {
    PENDING.clear();
    ACTIVE.remove();
  }

  CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("BlastPoints", blastPoints);
    return t;
  }

  void load(CompoundTag tag) {
    blastPoints = Math.clamp(tag.getInt("BlastPoints"), 0, MAX_BLAST_POINTS);
  }
}
