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

  static LivingEntity target(LivingCoreBlockEntity core) {
    if (!core.aggressive() || core.body().dead() || core.blueprint().busy() || core.ship() == null)
      return null;
    Vec3 root = BioshipBlueprint.physical(core, BlockPos.ZERO);
    LivingEntity nearest = null;
    double distance = 64;
    for (var entity :
        core.getLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(root, root).inflate(8),
                TentacleBlockEntity::attackable)) {
      double next = entity.getBoundingBox().getCenter().distanceToSqr(root);
      if (next < distance && core.vision().visible(core, entity.getBoundingBox().getCenter())) {
        nearest = entity;
        distance = next;
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
    Vec3 root = BioshipBlueprint.physical(core, BlockPos.ZERO),
        point = prey.getBoundingBox().getCenter();
    if (root.distanceToSqr(point) > 16) return;
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
