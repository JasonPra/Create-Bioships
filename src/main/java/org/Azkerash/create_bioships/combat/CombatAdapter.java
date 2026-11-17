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

  private static final Map<ServerSubLevel, CoreCombatHurtbox> CORES = new WeakHashMap<>();

  public static CoreCombatHurtbox coreTarget(ServerSubLevel body) {
    if (SubLevelHealth.status(body).dead()) return null;
    var current = CORES.get(body);
    if (current != null && !current.isRemoved()) return current;
    var cell =
        SubLevelHealth.blocks(body).stream()
            .filter(p -> body.getLevel().getBlockState(p).is(Create_bioships.CORE.get()))
            .findFirst()
            .orElse(null);
    if (cell == null) return null;
    var target = Create_bioships.CORE_HURTBOX.get().create(body.getLevel());
    if (target == null) return null;
    target.bindCore(body, cell);
    body.getLevel().addFreshEntity(target);
    CORES.put(body, target);
    return target;
  }

  private static final Map<ServerSubLevel, Map<net.minecraft.core.BlockPos, EyeCombatHurtbox>>
      EYES = new WeakHashMap<>();

  public static EyeCombatHurtbox eyeTarget(ServerSubLevel body, net.minecraft.core.BlockPos cell) {
    if (SubLevelHealth.status(body).dead()) return null;
    var eyes = EYES.computeIfAbsent(body, b -> new HashMap<>());
    var old = eyes.get(cell);
    if (old != null && !old.isRemoved()) return old;
    var eye = Create_bioships.EYE_HURTBOX.get().create(body.getLevel());
    if (eye == null) return null;
    eye.bindEye(body, cell);
    body.getLevel().addFreshEntity(eye);
    eyes.put(cell.immutable(), eye);
    return eye;
  }

  public static SubLevelCombatTarget hitTarget(
      ServerSubLevel body, net.minecraft.core.BlockPos cell, net.minecraft.core.Direction face) {
    var state = body.getLevel().getBlockState(cell);
    if (org.Azkerash.create_bioships.components.eye.EyeWoundBehavior.isWeakPoint(state, face))
      return state.is(Create_bioships.CORE.get()) ? coreTarget(body) : eyeTarget(body, cell);
    return target(body);
  }

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
            && CoreAssemblyRules.isCreature(body)) {
          target(server);
          coreTarget(server);
          for (var cell : SubLevelHealth.blocks(server))
            if (org.Azkerash.create_bioships.components.eye.EyeWoundBehavior.isEye(
                level.getBlockState(cell))) eyeTarget(server, cell);
        }
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
    var target = hitTarget(body, event.getPos(), event.getFace());
    if (target != null) {
      target.impact(event.getPos(), event.getFace());
      event.getEntity().attack(target);
    }
  }

  @SubscribeEvent
  public static void projectile(ProjectileImpactEvent event) {
    if (!(event.getProjectile().level() instanceof ServerLevel level)
        || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
    var body = CoreAssemblyRules.containing(level, hit.getBlockPos());
    if (body == null || !CoreAssemblyRules.isCreature(body)) return;
    var target = hitTarget(body, hit.getBlockPos(), hit.getDirection());
    if (target == null) return;
    target.impact(hit.getBlockPos(), hit.getDirection());
    // Collision has already been resolved against Sable's actual block shapes. Never use an
    // enclosing AABB as a substitute, which would count arrows passing through empty grid space.
    var projectile = event.getProjectile();
    var incoming = projectile.getDeltaMovement();
    boolean embedded = projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow;
    if (embedded)
      ProjectileAttachment.begin((net.minecraft.world.entity.projectile.AbstractArrow) projectile);
    try {
      ((ProjectileImpactInvoker) projectile)
          .core$hitEntity(new EntityHitResult(target, hit.getLocation()));
    } finally {
      if (embedded) ProjectileAttachment.end();
    }
    if (embedded && !projectile.isRemoved())
      ProjectileAttachment.attach(
          (net.minecraft.world.entity.projectile.AbstractArrow) projectile, body, hit, incoming);
    event.setCanceled(true);
  }

  public static void hurtFlash(ServerSubLevel body) {
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(
        body.getLevel(), new HurtFlashPayload(body.getUniqueId()));
  }
}
