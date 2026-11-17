package org.Azkerash.create_bioships;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.Azkerash.create_bioships.amoeba.ComponentTransport;

/** Idle exploration and injured escape share the same collision checks as ordinary travel. */
public final class BioshipAutonomy {
  private Vec3 wander, retreat, danger;
  private long nextWander, panicUntil, evaluated = Long.MIN_VALUE;
  private boolean fleeing;
  private long creeperUntil;

  public boolean creeperPanic() {
    return coreTime < creeperUntil;
  }

  private long coreTime;
  private java.util.UUID pilot;
  private long commandUntil;

  void command(LivingCoreBlockEntity core, net.minecraft.server.level.ServerPlayer player) {
    if (core.access().allows(core, player)) {
      pilot = player.getUUID();
      commandUntil = core.getLevel().getGameTime() + 200;
    }
  }

  boolean trustedPilot(LivingCoreBlockEntity core) {
    if (!AirshipPrototype.available()) return false;
    if (core.trustLevel() < PilotAuthority.TRUST_THRESHOLD) return false;
    if (pilot != null) {
      var player = core.getLevel().getPlayerByUUID(pilot);
      if (player != null
          && player.isAlive()
          && !player.isSpectator()
          && player.level() == core.getLevel()
          && core.access().allows(core, player)
          && PilotAuthority.obeys(
              core.trustLevel(),
              true,
              true,
              core.blueprint().aboard(core, player),
              core.getLevel().getGameTime(),
              commandUntil)) return true;
    }
    if (core.getLevel().getBlockEntity(core.layoutPos(DinghyGrowth.HELM))
            instanceof
            dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity helm
        && helm.held) {
      var position =
          core.ship()
              .logicalPose()
              .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(helm.getBlockPos()));
      return core.getLevel().players().stream()
          .anyMatch(
              player ->
                  player.isAlive()
                      && !player.isSpectator()
                      && core.access().allows(core, player)
                      && player.distanceToSqr(position) < 36);
    }
    return false;
  }

  public boolean fleeing() {
    return fleeing;
  }

  void assess(LivingCoreBlockEntity core) {
    long now = core.getLevel().getGameTime();
    if (evaluated == now) return;
    evaluated = now;
    coreTime = now;
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var creeper =
        core
            .getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.monster.Creeper.class,
                new net.minecraft.world.phys.AABB(here, here).inflate(12),
                e ->
                    e.isAlive()
                        && (BioshipDefense.threatening(core, e)
                            || e.distanceToSqr(here) < 16
                            || core.vision().visible(core, e.getBoundingBox().getCenter())))
            .stream()
            .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(here)))
            .orElse(null);
    if (creeper != null && !core.coreMotion().canAvoidBlast(core, creeper)) {
      danger = creeper.position();
      creeperUntil = now + 100;
    }
    var known = core.defense().dangerPosition(core);
    if (known != null && !creeperPanic()) danger = known;
    double ratio = core.body().health() / Math.max(1, core.body().capacity());
    if (ratio < .3 && known != null) panicUntil = now + 100;
    fleeing =
        !core.body().dead()
            && danger != null
            && (creeperPanic() || ratio < .5 && (known != null && ratio < .3 || now < panicUntil));
    if (!fleeing) retreat = null;
  }

  void activity(LivingCoreBlockEntity core) {
    nextWander = core.getLevel().getGameTime() + 100;
    wander = null;
  }

  Vec3 groundEscape(LivingCoreBlockEntity core) {
    assess(core);
    if (!fleeing || trustedPilot(core) && !creeperPanic()) return null;
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    if (retreat != null
        && here.distanceToSqr(retreat) > 1
        && core.crawl().reachableGoal(BlockPos.containing(retreat))) return retreat;
    var away = here.subtract(danger).multiply(1, 0, 1).normalize();
    if (away.lengthSqr() < .1) away = new Vec3(1, 0, 0);
    for (double turn : new double[] {0, .6, -.6, 1.2, -1.2}) {
      var request = here.add(away.yRot((float) turn).scale(10));
      var point = GroundRoute.groundedDestination(core, request);
      if (point != null
          && point.distanceToSqr(danger) > here.distanceToSqr(danger)
          && core.crawl().reachableGoal(BlockPos.containing(point))
          && new GroundRoute().plan(core, point)) return retreat = point;
    }
    return null;
  }

  Vec3 groundWander(LivingCoreBlockEntity core) {
    if ((!core.hatchling() && !core.huntingAllowed())
        || core.resting()
        || ComponentTransport.busy(core.amoebaComponents())) return null;
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    long now = core.getLevel().getGameTime();
    if (wander != null
        && here.distanceToSqr(wander) > 1
        && core.crawl().reachableGoal(BlockPos.containing(wander))) return wander;
    wander = null;
    if (nextWander == 0) {
      nextWander = now + 200;
      return null;
    }
    if (now < nextWander) return null;
    nextWander = now + 80;
    for (int i = 0; i < 8; i++) {
      double angle = core.getLevel().random.nextDouble() * Math.PI * 2;
      var point =
          GroundRoute.groundedDestination(
              core, here.add(Math.cos(angle) * 8, 0, Math.sin(angle) * 8));
      if (point != null
          && core.crawl().reachableGoal(BlockPos.containing(point))
          && new GroundRoute().plan(core, point)) return wander = point;
    }
    return null;
  }

  Vec3 flight(LivingCoreBlockEntity core, boolean escape) {
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    assess(core);
    if (escape && fleeing) {
      var away = here.subtract(danger).normalize();
      for (double turn : new double[] {0, .6, -.6, 1.2, -1.2}) {
        var direction = away.yRot((float) turn).add(0, .2, 0).normalize();
        if (GroundRoute.flightClear(core, here.add(direction.scale(6)))) return direction.scale(4);
      }
      return Vec3.ZERO;
    }
    if (!core.huntingAllowed() || core.resting()) return Vec3.ZERO;
    long now = core.getLevel().getGameTime();
    if (wander != null && here.distanceToSqr(wander) < 4) {
      wander = null;
      nextWander = now + 40;
    }
    if (wander == null && now >= nextWander) {
      nextWander = now + 80;
      for (int i = 0; i < 8; i++) {
        double angle = core.getLevel().random.nextDouble() * Math.PI * 2;
        var goal = here.add(Math.cos(angle) * 12, 0, Math.sin(angle) * 12);
        if (GroundRoute.flightClear(core, goal)) {
          wander = goal;
          break;
        }
      }
    }
    if (wander == null
        || !GroundRoute.flightClear(core, here.add(wander.subtract(here).normalize().scale(3)))) {
      wander = null;
      return Vec3.ZERO;
    }
    return wander.subtract(here).normalize().scale(1.2);
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    if (pilot != null) tag.putUUID("Pilot", pilot);
    tag.putLong("CommandUntil", commandUntil);
    tag.putLong("NextWander", nextWander);
    tag.putLong("CreeperUntil", creeperUntil);
    tag.putLong("PanicUntil", panicUntil);
    tag.putBoolean("Fleeing", fleeing);
    if (wander != null) tag.put("Wander", VisionDebug.vector(wander));
    if (retreat != null) tag.put("Retreat", VisionDebug.vector(retreat));
    if (danger != null) tag.put("Danger", VisionDebug.vector(danger));
    return tag;
  }

  void load(CompoundTag tag) {
    pilot = tag.hasUUID("Pilot") ? tag.getUUID("Pilot") : null;
    commandUntil = tag.getLong("CommandUntil");
    nextWander = tag.getLong("NextWander");
    creeperUntil = tag.getLong("CreeperUntil");
    coreTime = Long.MAX_VALUE;
    panicUntil = tag.getLong("PanicUntil");
    fleeing = tag.getBoolean("Fleeing");
    evaluated = Long.MIN_VALUE;
    wander = tag.contains("Wander") ? VisionDebug.vector(tag.getCompound("Wander")) : null;
    retreat = tag.contains("Retreat") ? VisionDebug.vector(tag.getCompound("Retreat")) : null;
    danger = tag.contains("Danger") ? VisionDebug.vector(tag.getCompound("Danger")) : null;
  }
}
