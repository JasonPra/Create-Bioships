package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.mixin.ProjectileImpactInvoker;

/** Minecraft targeting and attack transport, independent of health definitions and rendering. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CombatAdapter {
  private static final Map<ServerSubLevel, SubLevelCombatTarget> TARGETS = new WeakHashMap<>();

  private CombatAdapter() {}

  public static SubLevelCombatTarget target(ServerSubLevel body) {
    if (SubLevelHealth.status(body).dead()) return null;
    var old = TARGETS.get(body);
    if (old != null && !old.isRemoved()) return old;
    var target = Create_bioships.COMBAT_TARGET.get().create(body.getLevel());
    if (target == null) return null;
    target.bind(body);
    body.getLevel().addFreshEntity(target);
    TARGETS.put(body, target);
    return target;
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      if (level.getGameTime() % 10 != 0) continue;
      HostileCombatIntegration.tickBosses(level);
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      for (var body : container.getAllSubLevels())
        if (body instanceof ServerSubLevel server
            && !body.isRemoved()
            && CoreAssemblyRules.isCreature(body)) target(server);
    }
    TARGETS.entrySet().removeIf(e -> e.getKey().isRemoved() || e.getValue().isRemoved());
  }

  @SubscribeEvent
  public static void punch(PlayerInteractEvent.LeftClickBlock event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    var body = CoreAssemblyRules.containing(level, event.getPos());
    if (body == null || !CoreAssemblyRules.isCreature(body)) return;
    event.setCanceled(true);
    if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
    var target = target(body);
    if (target != null) event.getEntity().attack(target);
  }

  @SubscribeEvent
  public static void projectile(ProjectileImpactEvent event) {
    if (!(event.getProjectile().level() instanceof ServerLevel level)
        || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
    var body = CoreAssemblyRules.containing(level, hit.getBlockPos());
    if (body == null || !CoreAssemblyRules.isCreature(body)) return;
    var target = target(body);
    if (target == null) return;
    // Collision has already been resolved against Sable's actual block shapes. Never use an
    // enclosing AABB as a substitute, which would count arrows passing through empty grid space.
    ((ProjectileImpactInvoker) event.getProjectile())
        .core$hitEntity(new EntityHitResult(target, hit.getLocation()));
    event.setCanceled(true);
  }

  public static void hurtFlash(ServerSubLevel body) {
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(
        body.getLevel(), new HurtFlashPayload(body.getUniqueId()));
  }
}
