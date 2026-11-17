package org.Azkerash.create_bioships.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.Azkerash.create_bioships.registry.Create_bioships;
import org.Azkerash.create_bioships.TrustParticleBudget;

/** One subtle particle per rendered frame per burst, with bounded lifetime and queue size. */
@EventBusSubscriber(modid = Create_bioships.MOD_ID, value = Dist.CLIENT)
public final class TrustParticleFeedback {
  private static final String INBOX = "BioshipTrustBursts";
  private static final List<Burst> active = new ArrayList<>();
  private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

  private static final class Burst {
    final CompoundTag data;
    final long received;
    final TrustParticleBudget budget = new TrustParticleBudget();

    Burst(CompoundTag data) {
      this.data = data;
      received = data.getLong("Received");
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    var mc = Minecraft.getInstance();
    if (mc.level != lastLevel) {
      active.clear();
      lastLevel = mc.level;
    }
    if (mc.level == null || mc.player == null) return;
    var inbox = mc.player.getPersistentData().getList(INBOX, Tag.TAG_COMPOUND);
    for (int i = 0; i < inbox.size() && active.size() < 32; i++)
      active.add(new Burst(inbox.getCompound(i)));
    mc.player.getPersistentData().remove(INBOX);
    long now = System.nanoTime();
    active.removeIf(
        burst -> {
          var points = burst.data.getList("Points", Tag.TAG_COMPOUND);
          int point = burst.budget.next(now - burst.received, points.size());
          if (point < 0) return true;
          var p = points.getCompound(point);
          mc.level.addParticle(
              burst.data.getBoolean("Positive")
                  ? ParticleTypes.HAPPY_VILLAGER
                  : ParticleTypes.SMOKE,
              p.getDouble("X"),
              p.getDouble("Y"),
              p.getDouble("Z"),
              0,
              .015,
              0);
          return false;
        });
  }
}
