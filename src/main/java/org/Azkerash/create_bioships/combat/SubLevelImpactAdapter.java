package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.core.*;
import org.Azkerash.create_bioships.mixin.ProjectileImpactInvoker;

/** Grid-lifetime impact routing. The living identity flag survives health death and core loss. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class SubLevelImpactAdapter {
  private static final Map<ServerSubLevel, SubLevelImpactAdapter> ADAPTERS =
      new IdentityHashMap<>();
  private static final Map<ServerLevel, Map<UUID, Set<UUID>>> OBSERVERS = new WeakHashMap<>();
  private final ServerSubLevel body;
  private final Entity collisionProbe;
  private final Set<SurfaceWoundState.Surface> contacted = new HashSet<>();
  private long contactTick = Long.MIN_VALUE;
  private long lastFlash = Long.MIN_VALUE;

  private SubLevelImpactAdapter(ServerSubLevel body) {
    this.body = body;
    collisionProbe = EntityType.INTERACTION.create(body.getLevel());
  }

  public static SubLevelImpactAdapter get(ServerSubLevel body) {
    return body.isRemoved() || !SubLevelData.isLiving(body)
        ? null
        : ADAPTERS.computeIfAbsent(body, SubLevelImpactAdapter::new);
  }

  /** Unspawned narrow-phase probe: never a mob, target, health pool, or placement obstruction. */
  public Entity collisionProbe() {
    return collisionProbe;
  }

  /** Visual impact feedback belongs to grid lifetime, not the living mob target. */
  public void flash() {
    long now = body.getLevel().getGameTime();
    if (lastFlash != Long.MIN_VALUE && now - lastFlash < 10) return;
    lastFlash = now;
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(
        body.getLevel(), new HurtFlashPayload(body.getUniqueId()));
  }

  public void wound(BlockPos cell, Direction face) {
    if (body.isRemoved()
        || !SubLevelData.isLiving(body)
        || face == null
        || body.getLevel().getBlockState(cell).isAir()) return;
    if (SubLevelHealth.status(body).dead()) flash();
    long now = body.getLevel().getGameTime();
    if (contactTick != now) {
      contactTick = now;
      contacted.clear();
    }
    if (contacted.add(new SurfaceWoundState.Surface(cell.immutable(), face))
        || !SubLevelWounds.state(body).active(cell, face, now))
      SubLevelWounds.record(body, cell, face);
  }

  @SubscribeEvent
  public static void projectile(ProjectileImpactEvent event) {
    if (!(event.getProjectile().level() instanceof ServerLevel level)
        || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
    var body = CoreAssemblyRules.containing(level, hit.getBlockPos());
    var impact = body == null ? null : get(body);
    if (impact == null) return;
    var projectile = event.getProjectile();
    boolean dead = SubLevelHealth.status(body).dead();
    if (dead) {
      if (projectile instanceof ThrownPotion) return;
      impact.wound(hit.getBlockPos(), hit.getDirection());
      if (projectile instanceof AbstractArrow arrow) {
        ProjectileAttachment.attach(arrow, body, hit, arrow.getDeltaMovement());
        event.setCanceled(true);
      }
      return;
    }
    var target =
        projectile instanceof ThrownPotion
            ? CombatAdapter.target(body)
            : CombatAdapter.hitTarget(body, hit.getBlockPos(), hit.getDirection());
    if (target == null) return;
    target.impact(hit.getBlockPos(), hit.getDirection());
    var incoming = projectile.getDeltaMovement();
    boolean embedded = projectile instanceof AbstractArrow;
    if (embedded) ProjectileAttachment.begin((AbstractArrow) projectile);
    try {
      ((ProjectileImpactInvoker) projectile)
          .core$hitEntity(new EntityHitResult(target, hit.getLocation()));
    } finally {
      if (embedded) ProjectileAttachment.end();
    }
    if (embedded && !projectile.isRemoved())
      ProjectileAttachment.attach((AbstractArrow) projectile, body, hit, incoming);
    event.setCanceled(true);
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % 20 != 0) return;
    var active = Collections.newSetFromMap(new IdentityHashMap<ServerSubLevel, Boolean>());
    for (var level : event.getServer().getAllLevels()) {
      var container = SubLevelContainer.getContainer(level);
      if (container == null) continue;
      var bodies = new ArrayList<ServerSubLevel>();
      for (var entry : container.getAllSubLevels())
        if (entry instanceof ServerSubLevel body && get(body) != null) {
          active.add(body);
          bodies.add(body);
        }
      var seen = OBSERVERS.computeIfAbsent(level, l -> new HashMap<>());
      var players = new HashSet<UUID>();
      for (var player : level.players()) {
        players.add(player.getUUID());
        var known = seen.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        var present = new HashSet<UUID>();
        for (var body : bodies) {
          present.add(body.getUniqueId());
          if (known.add(body.getUniqueId()))
            for (var wound : SubLevelWounds.state(body).active(level.getGameTime()))
              net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                  player,
                  new WoundPayload(
                      body.getUniqueId(),
                      wound.surface().cell(),
                      wound.surface().face(),
                      wound.at(),
                      wound.duration()));
        }
        known.retainAll(present);
      }
      seen.keySet().retainAll(players);
    }
    ADAPTERS.keySet().retainAll(active);
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    ADAPTERS.clear();
    OBSERVERS.clear();
  }
}
