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
  private final TentacleStrike strike = new TentacleStrike();

  public boolean sweeping(long now) {
    return strike.sweeping(now);
  }

  private java.util.UUID attacker;
  private long threatenedUntil;
  private Vec3 lastThreat;

  void remember(LivingCoreBlockEntity core, LivingEntity entity) {
    if (!core.aggressive() && core.access().allows(core, entity)) return;
    core.vision().rememberEntity(entity);
    attacker = entity.getUUID();
    lastThreat = entity.position();
    threatenedUntil = core.getLevel().getGameTime() + 200;
    core.setChanged();
  }

  Vec3 dangerPosition(LivingCoreBlockEntity core) {
    var threat = target(core);
    if (threat != null && threatening(core, threat)) {
      lastThreat = core.vision().knownPosition(threat);
      threatenedUntil = core.getLevel().getGameTime() + 200;
    }
    return core.getLevel().getGameTime() < threatenedUntil ? lastThreat : null;
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
    tag.put("Strike", strike.save());
    if (lastThreat != null) tag.put("LastThreat", VisionDebug.vector(lastThreat));
    return tag;
  }

  void load(net.minecraft.nbt.CompoundTag tag) {
    lastThreat =
        tag.contains("LastThreat") ? VisionDebug.vector(tag.getCompound("LastThreat")) : null;
    attacker = tag.hasUUID("Attacker") ? tag.getUUID("Attacker") : null;
    threatenedUntil = tag.getLong("ThreatenedUntil");
    cooldown = Math.clamp(tag.getInt("Cooldown"), 0, 40);
    strike.load(tag.getCompound("Strike"));
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
    if (nearest != null) core.vision().rememberEntity(nearest);
    else {
      var remembered = core.vision().rememberedEntity(core);
      if (remembered != null
          && TentacleBlockEntity.attackable(remembered)
          && (threatening(core, remembered)
              || core.aggressive()
                  && core.vision().knownPosition(remembered).distanceToSqr(root) <= 64))
        nearest = remembered;
    }
    return nearest;
  }

  private static boolean eligiblePrey(LivingCoreBlockEntity core, LivingEntity entity) {
    return TentacleBlockEntity.attackable(entity)
        && TentacleBlockEntity.edible(entity)
        && !core.access().allows(core, entity)
        && !(entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame())
        && !(entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse
            && horse.isTamed())
        && !entity.isPassenger();
  }

  static LivingEntity prey(LivingCoreBlockEntity core) {
    if (!core.wantsToHunt()) return null;
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var selected =
        core
            .getLevel()
            .getEntitiesOfClass(
                LivingEntity.class,
                new AABB(here, here).inflate(core.vision().maximumDistance(core)),
                e -> eligiblePrey(core, e) && sensesPrey(core, e))
            .stream()
            .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(here)))
            .orElse(null);
    if (selected != null) {
      core.vision().rememberEntity(selected);
      return selected;
    }
    var remembered = core.vision().rememberedEntity(core);
    return remembered != null && eligiblePrey(core, remembered) ? remembered : null;
  }

  private static boolean sensesPrey(LivingCoreBlockEntity core, LivingEntity entity) {
    if (core.vision().visible(core, entity.getBoundingBox().getCenter())) return true;
    // A creature already touching the feeding face is detectable even with the core concealed.
    if (!core.hasMouth()
        || entity.getBoundingBox().getCenter().distanceToSqr(core.mouthPosition(core.ship())) > 9)
      return false;
    for (var actor : BioshipIntegration.blockEntities(core.ship()))
      if (actor instanceof TentacleBlockEntity limb) {
        var root =
            core.ship()
                .logicalPose()
                .transformPosition(Vec3.atCenterOf(limb.getBlockPos()))
                .add(0, -.7, 0);
        if (limb.visible(root, entity.getBoundingBox().getCenter(), entity, core, core.ship()))
          return true;
      }
    return false;
  }

  void tick(LivingCoreBlockEntity core) {
    if (cooldown > 0) cooldown--;
    if (core.isMature()
        && BioshipIntegration.blockEntities(core.ship()).stream()
            .anyMatch(
                e -> e instanceof TentacleBlockEntity limb && limb.owner(core.ship()) == core))
      return;
    var level = (ServerLevel) core.getLevel();
    var selected = core.brain().attackTarget(core);
    if (selected == null) {
      strike.clear();
      core.crawl().reach(null);
      return;
    }
    var prey = strike.active() ? strike.victim(level) : selected;
    if (prey != selected) {
      strike.clear();
      prey = selected;
    }
    if (prey instanceof net.minecraft.world.entity.monster.Creeper
        && core.autonomy().creeperPanic()) {
      strike.clear();
      core.crawl().reach(null);
      return;
    }
    if (strike.active()) {
      var root =
          BioshipReach.anchor(
              core,
              prey == null
                  ? BioshipBlueprint.physical(core, BlockPos.ZERO)
                  : prey.getBoundingBox().getCenter());
      core.crawl().reach(strike.tip(root, level.getGameTime(), 4));
      extending = true;
      strike.tick(core, root, prey, 4, 4);
      if (strike.finished(level.getGameTime())) {
        strike.clear();
        cooldown = 28;
        core.crawl().reach(null);
        extending = false;
      }
      level.sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
      return;
    }
    if (prey == null || cooldown > 0) {
      if (extending) {
        core.crawl().reach(null);
        extending = false;
      }
      return;
    }
    var point = prey.getBoundingBox().getCenter();
    if (BioshipReach.anchor(core, point).distanceToSqr(point) > 16
        || !BioshipReach.clear(core, point, false)) return;
    strike.begin(prey, level.getGameTime());
    extending = true;
    core.crawl().reach(strike.tip(BioshipReach.anchor(core, point), level.getGameTime(), 4));
    level.sendBlockUpdated(core.getBlockPos(), core.getBlockState(), core.getBlockState(), 2);
  }
}
