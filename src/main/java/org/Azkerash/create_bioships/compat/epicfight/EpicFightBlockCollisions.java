package org.Azkerash.create_bioships.compat.epicfight;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.combat.*;
import org.Azkerash.create_bioships.compat.epicfight.mixin.EpicFightColliderAccess;
import org.Azkerash.create_bioships.core.*;

/**
 * Optional bridge: Epic Fight's weapon collider tests individual tissue shapes, including holes.
 */
public final class EpicFightBlockCollisions {
  private EpicFightBlockCollisions() {}

  /**
   * A swept collision with tissue remains valid when a charging attacker has entered that tissue.
   */
  public static boolean visibleContact(Entity target, Entity watcher) {
    if (!(target instanceof SubLevelCombatTarget adapter)
        || !(watcher.level() instanceof net.minecraft.server.level.ServerLevel level)) {
      return false;
    }
    var body = adapter.body();
    if (body == null) {
      return false;
    }
    var hit =
        level.clip(
            new net.minecraft.world.level.ClipContext(
                watcher.getEyePosition(),
                target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                watcher));
    if (hit.getType() != HitResult.Type.BLOCK
        || CoreAssemblyRules.containing(level, hit.getBlockPos()) != body) {
      return false;
    }
    if (adapter instanceof EyeCombatHurtbox eye && !eye.cell().equals(hit.getBlockPos())) {
      return false;
    }
    adapter.impact(hit.getBlockPos(), hit.getDirection());
    return true;
  }

  public static List<Entity> collectSamples(
      Entity attacker, List<Entity> original, List<?> samples) {
    if (attacker.level().isClientSide) {
      return original;
    }
    var hits = new ArrayList<>(original);
    hits.removeIf(e -> e instanceof SubLevelCombatTarget);
    for (var sample : samples) {
      hits.addAll(collect(sample, attacker, List.of()));
    }
    return onePerGrid(hits);
  }

  public static List<Entity> onePerGrid(List<Entity> hits) {
    var result = new ArrayList<Entity>();
    var grids = new LinkedHashMap<UUID, SubLevelCombatTarget>();
    for (var entity : hits) {
      if (!(entity instanceof SubLevelCombatTarget target)) {
        result.add(entity);
        continue;
      }
      var body = target.body();
      if (body == null) {
        continue;
      }
      var old = grids.get(body.getUniqueId());
      if (old == null || target instanceof EyeCombatHurtbox && !(old instanceof EyeCombatHurtbox)) {
        grids.put(body.getUniqueId(), target);
      }
    }
    result.addAll(grids.values());
    return result;
  }

  public static List<Entity> collect(Object collider, Entity attacker, List<Entity> original) {
    if (attacker.level().isClientSide) {
      return original;
    }
    var result = new ArrayList<>(original);
    result.removeIf(e -> e instanceof SubLevelCombatTarget);
    var container = SubLevelContainer.getContainer(attacker.level());
    if (container == null) {
      return result;
    }
    var access = (EpicFightColliderAccess) collider;
    var attackBox = access.core$bounds();
    for (var entry : container.getAllSubLevels()) {
      if (!(entry instanceof ServerSubLevel body)
          || body.isRemoved()
          || !SubLevelData.isLiving(body)) {
        continue;
      }
      if (!body.boundingBox().toMojang().intersects(attackBox)) {
        continue;
      }
      SubLevelCombatTarget selected = null;
      Vec3 selectedAim = null;
      net.minecraft.core.BlockPos selectedCell = null;
      Direction selectedFace = null;
      double nearest = Double.POSITIVE_INFINITY;
      boolean weak = false;
      var impacts = SubLevelImpactAdapter.get(body);
      if (impacts == null) {
        continue;
      }
      boolean dead = SubLevelHealth.status(body).dead();
      var probe = impacts.collisionProbe();
      var oldBox = probe.getBoundingBox();
      try {
        for (var cell : SubLevelHealth.blocks(body)) {
          var state = body.getLevel().getBlockState(cell);
          var shape = state.getCollisionShape(body.getLevel(), cell);
          for (var local : shape.toAabbs()) {
            var logical = local.move(cell);
            var bounds = new BoundingBox3d(logical);
            var world = bounds.transform(body.logicalPose(), bounds).toMojang();
            if (!world.intersects(attackBox)) {
              continue;
            }
            probe.setBoundingBox(world);
            if (!access.core$collides(probe)) {
              continue;
            }
            var from = body.logicalPose().transformPositionInverse(attacker.getEyePosition());
            var delta = from.subtract(logical.getCenter());
            var face = Direction.getNearest(delta.x, delta.y, delta.z);
            if (dead) {
              var aim =
                  body.logicalPose().transformPosition(logical.getCenter().relative(face, .53));
              var visible =
                  attacker
                      .level()
                      .clip(
                          new net.minecraft.world.level.ClipContext(
                              attacker.getEyePosition(),
                              aim,
                              net.minecraft.world.level.ClipContext.Block.COLLIDER,
                              net.minecraft.world.level.ClipContext.Fluid.NONE,
                              attacker));
              if (visible.getType() == HitResult.Type.BLOCK
                  && (CoreAssemblyRules.containing(
                              (net.minecraft.server.level.ServerLevel) attacker.level(),
                              visible.getBlockPos())
                          != body
                      || !visible.getBlockPos().equals(cell))) {
                continue;
              }
            }
            var target = dead ? null : CombatAdapter.hitTarget(body, cell, face);
            boolean critical =
                org.Azkerash.create_bioships.components.logic.eye.EyeWoundBehavior.isWeakPoint(
                    state, face);
            double distance = world.distanceToSqr(attacker.getEyePosition());
            if (selectedCell != null
                && (!critical && weak || critical == weak && distance >= nearest)) {
              continue;
            }
            selected = target;
            selectedCell = cell;
            selectedFace = face;
            nearest = distance;
            weak = critical;
            double extent =
                switch (face.getAxis()) {
                  case X -> logical.getXsize();
                  case Y -> logical.getYsize();
                  case Z -> logical.getZsize();
                };
            selectedAim =
                body.logicalPose()
                    .transformPosition(logical.getCenter().relative(face, extent / 2 + .03));
          }
        }
      } finally {
        probe.setBoundingBox(oldBox);
      }
      if (dead && selectedCell != null) {
        impacts.wound(selectedCell, selectedFace);
      }
      if (selected != null) {
        selected.impact(selectedCell, selectedFace);
        selected.focusForAttack(selectedAim);
        var owner = org.Azkerash.create_bioships.core.SubLevelCreatureParts.root(body);
        result.removeIf(
            e ->
                e instanceof org.Azkerash.create_bioships.combat.SubLevelCombatTarget t
                    && org.Azkerash.create_bioships.core.SubLevelCreatureParts.root(t.body())
                        == owner);
        result.add(selected);
      }
    }
    return result;
  }
}
