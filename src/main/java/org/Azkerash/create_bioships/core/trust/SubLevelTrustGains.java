package org.Azkerash.create_bioships.core.trust;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import org.Azkerash.create_bioships.core.*;

/** Shared positive trust feedback for explicit player care actions. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "create_bioships")
public final class SubLevelTrustGains {
  private static final Map<ServerSubLevel, Long> PARTICLES = new WeakHashMap<>();

  private SubLevelTrustGains() {}

  public static void cancelParticles(ServerSubLevel body) {
    PARTICLES.remove(SubLevelCreatureParts.root(body));
  }

  public static void add(ServerSubLevel body, double amount) {
    if (!(amount > 0) || !Double.isFinite(amount) || !SubLevelCreatureParts.available(body)) return;
    body = SubLevelCreatureParts.root(body);
    if (body.isRemoved() || !SubLevelData.isLiving(body) || SubLevelHealth.status(body).dead())
      return;
    double before = SubLevelData.trust(body);
    SubLevelData.trust(body, before + amount);
    if (SubLevelData.trust(body) > before)
      PARTICLES.putIfAbsent(body, body.getLevel().getGameTime() + 5);
  }

  @net.neoforged.bus.api.SubscribeEvent
  public static void particles(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    PARTICLES
        .entrySet()
        .removeIf(
            entry -> {
              var body = entry.getKey();
              if (body.isRemoved() || SubLevelHealth.status(body).dead()) return true;
              if (body.getLevel().getGameTime() < entry.getValue()) return false;
              var cells = SubLevelHealth.blocks(body);
              int count = Math.min(6, cells.size());
              for (int i = 0; i < count; i++) {
                var cell =
                    cells.get(
                        (i * cells.size() / count + body.getLevel().random.nextInt(cells.size()))
                            % cells.size());
                var at =
                    body.logicalPose()
                        .transformPosition(
                            net.minecraft.world.phys.Vec3.atCenterOf(cell).add(0, .65, 0));
                body.getLevel()
                    .sendParticles(
                        net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        at.x,
                        at.y,
                        at.z,
                        1,
                        .12,
                        .12,
                        .12,
                        .01);
              }
              return true;
            });
  }
}
