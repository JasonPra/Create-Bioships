package org.Azkerash.create_bioships.combat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.Azkerash.create_bioships.Create_bioships;
import org.Azkerash.create_bioships.components.ComponentRegistry;
import org.Azkerash.create_bioships.core.*;

/** Minecraft targeting and attack transport, independent of health definitions and rendering. */
@EventBusSubscriber(modid = Create_bioships.MODID)
public final class CombatAdapter {
  private static final Map<ServerSubLevel, SubLevelCombatTarget> TARGETS = new WeakHashMap<>();

  private static final Map<ServerSubLevel, CoreCombatHurtbox> CORES = new WeakHashMap<>();

  public static CoreCombatHurtbox coreTarget(ServerSubLevel body) {
    if (SubLevelHealth.status(body).dead()) {
      return null;
    }
    var current = CORES.get(body);
    if (current != null && !current.isRemoved()) {
      return current;
    }
    var cell =
        SubLevelHealth.blocks(body).stream()
            .filter(p -> body.getLevel().getBlockState(p).is(ComponentRegistry.CORE.get()))
            .findFirst()
            .orElse(null);
    if (cell == null) {
      return null;
    }
    var target = Create_bioships.CORE_HURTBOX.get().create(body.getLevel());
    if (target == null) {
      return null;
    }
    target.bindCore(body, cell);
    body.getLevel().addFreshEntity(target);
    CORES.put(body, target);
    return target;
  }

  private static final Map<ServerSubLevel, Map<net.minecraft.core.BlockPos, EyeCombatHurtbox>>
      EYES = new WeakHashMap<>();

  public static EyeCombatHurtbox eyeTarget(ServerSubLevel body, net.minecraft.core.BlockPos cell) {
    if (SubLevelHealth.status(body).dead()) {
      return null;
    }
    var eyes = EYES.computeIfAbsent(body, b -> new HashMap<>());
    var old = eyes.get(cell);
    if (old != null && !old.isRemoved()) {
      return old;
    }
    var eye = Create_bioships.EYE_HURTBOX.get().create(body.getLevel());
    if (eye == null) {
      return null;
    }
    eye.bindEye(body, cell);
    body.getLevel().addFreshEntity(eye);
    eyes.put(cell.immutable(), eye);
    return eye;
  }

  public static SubLevelCombatTarget hitTarget(
      ServerSubLevel body, net.minecraft.core.BlockPos cell, net.minecraft.core.Direction face) {
    var state = body.getLevel().getBlockState(cell);
    var result =
        org.Azkerash.create_bioships.components.logic.eye.EyeWoundBehavior.isWeakPoint(state, face)
            ? state.is(ComponentRegistry.CORE.get()) ? coreTarget(body) : eyeTarget(body, cell)
            : target(body);
    if (result != null) {
      result.impact(cell, face);
    }
    return result;
  }

  public static void saveEffects(ServerSubLevel body, SubLevelCombatTarget target) {
    var list = new net.minecraft.nbt.ListTag();
    for (var effect : target.getActiveEffects()) {
      list.add(effect.save());
    }
    if (body.getUserDataTag()
        .getList(org.Azkerash.create_bioships.core.SubLevelData.EFFECTS, 10)
        .equals(list)) {
      return;
    }
    var root = body.getUserDataTag().copy();
    root.put(org.Azkerash.create_bioships.core.SubLevelData.EFFECTS, list);
    body.setUserDataTag(root);
  }

  public static void restoreEffects(ServerSubLevel body, SubLevelCombatTarget target) {
    var root = body.getUserDataTag();
    if (root == null) {
      return;
    }
    for (var entry : root.getList(org.Azkerash.create_bioships.core.SubLevelData.EFFECTS, 10)) {
      var effect =
          net.minecraft.world.effect.MobEffectInstance.load((net.minecraft.nbt.CompoundTag) entry);
      if (effect != null) {
        target.addEffect(effect);
      }
    }
  }

  private CombatAdapter() {}

  public static SubLevelCombatTarget target(ServerSubLevel body) {
    if (!SubLevelCreatureParts.available(body)) {
      return null;
    }
    body = SubLevelCreatureParts.root(body);
    if (SubLevelHealth.status(body).dead()) {
      return null;
    }
    var old = TARGETS.get(body);
    if (old != null && !old.isRemoved()) {
      return old;
    }
    var target = Create_bioships.COMBAT_TARGET.get().create(body.getLevel());
    if (target == null) {
      return null;
    }
    target.bind(body);
    body.getLevel().addFreshEntity(target);
    TARGETS.put(body, target);
    return target;
  }

  /** Route native dragon body-part contact against tissue, not just the small targeting proxy. */
  @SubscribeEvent
  public static void dragonContact(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
    if (!(event.getEntity()
            instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon)
        || !(dragon.level() instanceof ServerLevel level)
        || dragon.getPhaseManager().getCurrentPhase().getPhase()
            != net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase
                .CHARGING_PLAYER) {
      return;
    }
    // Epic Fight keeps hurtTime at 2 to suppress vanilla proxy contact during its boss phases.
    if (dragon.hurtTime > 2) {
      return;
    }
    var container = SubLevelContainer.getContainer(level);
    if (container == null) {
      return;
    }
    var head = dragon.head.getBoundingBox().inflate(1);
    var neck =
        Arrays.stream(dragon.getSubEntities())
            .filter(part -> part.name.equals("neck"))
            .findFirst()
            .orElse(dragon.head)
            .getBoundingBox()
            .inflate(1);
    var wings =
        Arrays.stream(dragon.getSubEntities())
            .filter(part -> part.name.equals("wing"))
            .map(part -> part.getBoundingBox().inflate(4, 2, 4).move(0, -2, 0))
            .toList();
    var bounds = head.minmax(neck);
    for (var wing : wings) {
      bounds = bounds.minmax(wing);
    }
    for (var entry : container.getAllSubLevels()) {
      if (!(entry instanceof ServerSubLevel body)
          || body.isRemoved()
          || !CoreAssemblyRules.isCreature(body)
          || SubLevelHealth.status(body).dead()
          || !body.boundingBox().toMojang().intersects(bounds)) {
        continue;
      }
      net.minecraft.core.BlockPos contact = null;
      float damage = 0;
      for (var cell : SubLevelHealth.blocks(body)) {
        for (var local : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs()) {
          var box = CustomHitboxes.create(body, local.move(cell)).getBounds();
          float hit =
              head.intersects(box) || neck.intersects(box)
                  ? 10
                  : wings.stream().anyMatch(wing -> wing.intersects(box)) ? 5 : 0;
          if (hit > damage) {
            damage = hit;
            contact = cell;
          }
        }
      }
      if (contact != null) {
        var point = body.logicalPose().transformPosition(Vec3.atCenterOf(contact));
        var face =
            net.minecraft.core.Direction.getNearest(
                dragon.getX() - point.x, dragon.getY() - point.y, dragon.getZ() - point.z);
        var target = hitTarget(body, contact, face);
        if (target != null) {
          target.hurt(level.damageSources().mobAttack(dragon), damage);
        }
      }
    }
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    for (var level : event.getServer().getAllLevels()) {
      if (level.getGameTime() % 10 != 0) {
        continue;
      }
      HostileCombatIntegration.tickBosses(level);
      var container = SubLevelContainer.getContainer(level);
      if (container == null) {
        continue;
      }
      for (var body : container.getAllSubLevels()) {
        if (body instanceof ServerSubLevel server
            && !body.isRemoved()
            && CoreAssemblyRules.isCreature(body)) {
          target(server);
          coreTarget(server);
          for (var cell : SubLevelHealth.blocks(server)) {
            if (org.Azkerash.create_bioships.components.logic.eye.EyeWoundBehavior.isEye(
                level.getBlockState(cell))) {
              eyeTarget(server, cell);
            }
          }
        }
      }
    }
    TARGETS.entrySet().removeIf(e -> e.getKey().isRemoved() || e.getValue().isRemoved());
  }

  /** Vanilla potion/cloud volumes select one recipient for each grid, using real block shapes. */
  public static java.util.List<net.minecraft.world.entity.LivingEntity> potionTargets(
      net.minecraft.world.level.Level level,
      net.minecraft.world.phys.AABB bounds,
      java.util.List<net.minecraft.world.entity.LivingEntity> nativeTargets) {
    if (!(level instanceof ServerLevel server)) {
      return nativeTargets;
    }
    var result = new java.util.ArrayList<>(nativeTargets);
    result.removeIf(e -> e instanceof SubLevelCombatTarget);
    var container = SubLevelContainer.getContainer(server);
    if (container == null) {
      return result;
    }
    for (var entry :
        container.queryIntersecting(new dev.ryanhcode.sable.companion.math.BoundingBox3d(bounds))) {
      if (!(entry instanceof ServerSubLevel body)
          || body.isRemoved()
          || !CoreAssemblyRules.isCreature(body)
          || SubLevelHealth.status(body).dead()) {
        continue;
      }
      net.minecraft.world.phys.Vec3 nearest = null;
      double distance = Double.POSITIVE_INFINITY;
      for (var cell : SubLevelHealth.blocks(body)) {
        for (var local : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs()) {
          var hitbox = CustomHitboxes.create(body, local.move(cell));
          var world = hitbox.getBounds();
          if (!world.intersects(bounds)) {
            continue;
          }
          double d = world.getCenter().distanceToSqr(bounds.getCenter());
          if (d < distance) {
            nearest = world.getCenter();
            distance = d;
          }
        }
      }
      if (nearest != null) {
        var target = target(body);
        if (target != null) {
          target.setPos(nearest);
          if (!result.contains(target)) {
            result.add(target);
          }
        }
      }
    }
    return result;
  }

  /** Use the weapon's vanilla sweep box, not the invisible adapters' incidental positions. */
  public static java.util.List<net.minecraft.world.entity.LivingEntity> sweepTargets(
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.phys.AABB bounds,
      net.minecraft.world.entity.Entity primary,
      java.util.List<net.minecraft.world.entity.LivingEntity> nativeTargets) {
    if (!(player.level() instanceof ServerLevel level)) {
      return nativeTargets;
    }
    var result = new java.util.ArrayList<>(nativeTargets);
    result.removeIf(e -> e instanceof SubLevelCombatTarget);
    var primaryBody =
        primary instanceof SubLevelCombatTarget target
            ? SubLevelCreatureParts.root(target.body())
            : null;
    var container = SubLevelContainer.getContainer(level);
    if (container == null) {
      return result;
    }
    for (var entry : container.getAllSubLevels()) {
      if (!(entry instanceof ServerSubLevel body)
          || SubLevelCreatureParts.root(body) == primaryBody
          || body.isRemoved()
          || !CoreAssemblyRules.isCreature(body)
          || SubLevelHealth.status(body).dead()
          || !body.boundingBox().toMojang().intersects(bounds)) {
        continue;
      }
      SubLevelCombatTarget selected = null;
      net.minecraft.core.BlockPos selectedCell = null;
      net.minecraft.core.Direction selectedFace = null;
      net.minecraft.world.phys.Vec3 aim = null;
      for (var cell : SubLevelHealth.blocks(body)) {
        var state = level.getBlockState(cell);
        for (var local : state.getCollisionShape(level, cell).toAabbs()) {
          var logical = local.move(cell);
          var hitbox = CustomHitboxes.create(body, logical);
          if (!hitbox.intersects(bounds)) {
            continue;
          }
          var from =
              body.logicalPose()
                  .transformPositionInverse(player.getEyePosition())
                  .subtract(logical.getCenter());
          var face = net.minecraft.core.Direction.getNearest(from.x, from.y, from.z);
          var target = hitTarget(body, cell, face);
          if (selected instanceof EyeCombatHurtbox
              || selected != null && !(target instanceof EyeCombatHurtbox)) {
            continue;
          }
          selected = target;
          selectedCell = cell;
          selectedFace = face;
          double extent =
              switch (face.getAxis()) {
                case X -> logical.getXsize();
                case Y -> logical.getYsize();
                case Z -> logical.getZsize();
              };
          aim =
              body.logicalPose()
                  .transformPosition(logical.getCenter().relative(face, extent / 2 + .03));
        }
      }
      if (selected != null) {
        selected.impact(selectedCell, selectedFace);
        selected.focusForAttack(aim);
        var owner = SubLevelCreatureParts.root(body);
        result.removeIf(
            e ->
                e instanceof SubLevelCombatTarget t
                    && SubLevelCreatureParts.root(t.body()) == owner);
        result.add(selected);
      }
    }
    return result;
  }

  @SubscribeEvent
  public static void punch(PlayerInteractEvent.LeftClickBlock event) {
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }
    var body = CoreAssemblyRules.containing(level, event.getPos());
    var impact = body == null ? null : SubLevelImpactAdapter.get(body);
    if (impact == null) {
      return;
    }
    if (SubLevelHealth.status(body).dead()) {
      if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
        impact.wound(event.getPos(), event.getFace());
      }
      return; // Keep ordinary corpse mining intact.
    }
    event.setCanceled(true);
    if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
      return;
    }
    var target = hitTarget(body, event.getPos(), event.getFace());
    if (target != null) {
      target.impact(event.getPos(), event.getFace());
      target.focusForAttack(
          body.logicalPose()
              .transformPosition(
                  net.minecraft.world.phys.Vec3.atCenterOf(event.getPos())
                      .relative(event.getFace(), .55)));
      event.getEntity().attack(target);
    }
  }

  public static void hurtFlash(ServerSubLevel body) {
    var impacts = SubLevelImpactAdapter.get(body);
    if (impacts != null) {
      impacts.flash();
    }
  }
}
