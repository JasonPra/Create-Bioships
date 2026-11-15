package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small tissue arms can lash out before the organism has grown its propulsion flagella. */
final class BioshipDefense {
  private int cooldown;
  private boolean extending;
  private java.util.UUID attacker;
  private long threatenedUntil;

  void remember(LivingCoreBlockEntity core, LivingEntity entity) {
    if (!core.aggressive() && core.access().allows(core, entity)) return;
    attacker = entity.getUUID();
    threatenedUntil = core.getLevel().getGameTime() + 200;
    core.setChanged();
  }

  static boolean threatening(LivingCoreBlockEntity core, LivingEntity entity) {
    return entity instanceof net.minecraft.world.entity.Mob mob
            && mob.getTarget() instanceof BioshipTargetEntity target
            && target.belongsTo(core)
        || entity.getUUID().equals(core.defense().attacker)
            && core.getLevel().getGameTime() < core.defense().threatenedUntil;
  }

  static net.minecraft.world.entity.monster.Creeper creeperThreat(LivingCoreBlockEntity core) {
    if (core.ship() == null || core.body().dead()) return null;
    var root = BioshipBlueprint.physical(core, BlockPos.ZERO);
    return core
        .getLevel()
        .getEntitiesOfClass(
            net.minecraft.world.entity.monster.Creeper.class,
            new AABB(root, root).inflate(32),
            e ->
                e.isAlive()
                    && (threatening(core, e) || e.isIgnited() || e.getSwellDir() > 0)
                    && e.position().distanceToSqr(root) < Math.pow(e.isPowered() ? 8 : 5, 2))
        .stream()
        .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(root)))
        .orElse(null);
  }

  net.minecraft.nbt.CompoundTag save() {
    var tag = new net.minecraft.nbt.CompoundTag();
    if (attacker != null) tag.putUUID("Attacker", attacker);
    tag.putLong("ThreatenedUntil", threatenedUntil);
    tag.putInt("Cooldown", cooldown);
    return tag;
  }

  void load(net.minecraft.nbt.CompoundTag tag) {
    attacker = tag.hasUUID("Attacker") ? tag.getUUID("Attacker") : null;
    threatenedUntil = tag.getLong("ThreatenedUntil");
    cooldown = Math.clamp(tag.getInt("Cooldown"), 0, 20);
  }

  static LivingEntity target(LivingCoreBlockEntity core) {
    if (core.body().dead() || core.ship() == null) return null;
    Vec3 root = BioshipBlueprint.physical(core, BlockPos.ZERO);
    LivingEntity nearest = null;
    double distance = Double.MAX_VALUE;
    for (var entity :
        core.getLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(root, root).inflate(32),
                TentacleBlockEntity::attackable)) {
      double next = entity.getBoundingBox().getCenter().distanceToSqr(root);
      boolean hostile = threatening(core, entity);
      double score =
          next
              - (entity instanceof net.minecraft.world.entity.monster.Creeper creeper
                      && (creeper.getSwellDir() > 0 || creeper.isIgnited())
                  ? 2048
                  : hostile ? 1024 : 0);
      if ((core.aggressive() && next <= 64 || hostile)
          && score < distance
          && (core.vision().visible(core, entity.getBoundingBox().getCenter())
              || threatening(core, entity)
                  && BioshipReach.clear(core, entity.getBoundingBox().getCenter(), false))) {
        nearest = entity;
        distance = score;
      }
    }
    return nearest;
  }

  void tick(LivingCoreBlockEntity core) {
    if (cooldown > 0) cooldown--;
    var prey = target(core);
    if (prey == null) {
      if (extending) {
        core.crawl().reach(null);
        extending = false;
      }
      return;
    }
    Vec3 point = prey.getBoundingBox().getCenter();
    if (BioshipReach.anchor(core, point).distanceToSqr(point) > 16
        || !BioshipReach.clear(core, point, false)) return;
    core.crawl().reach(point);
    extending = true;
    if (cooldown == 0) {
      var level = (ServerLevel) core.getLevel();
      prey.hurt(level.damageSources().generic(), 2);
      cooldown = 20;
    }
    if (core.getLevel().getGameTime() % 5 == 0)
      core.getLevel()
          .sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }
}
