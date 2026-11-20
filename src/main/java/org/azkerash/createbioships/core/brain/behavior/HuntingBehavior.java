package org.azkerash.createbioships.core.brain.behavior;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.azkerash.createbioships.core.*;
import org.azkerash.createbioships.core.access.SubLevelPermissions;
import org.azkerash.createbioships.core.brain.*;
import org.azkerash.createbioships.core.brain.hunting.*;
import org.azkerash.createbioships.core.pathfinding.*;
import org.azkerash.createbioships.core.vision.SubLevelVision;

/**
 * Sight-driven pursuit; routes and targets are transient, while the whitelist lives on the card.
 */
public final class HuntingBehavior implements BrainBehavior {
  public static final ResourceLocation ID = ResourceLocation.parse("create_bioships:hunt");
  private static final int REPLAN_TICKS = 20, MOTOR_LEASE_TICKS = 20;
  private static final double CHASE_SPEED = 1.6, REPLAN_DISTANCE_SQUARED = 4;
  private static final double APPROACH_MARGIN = 1, ROUTE_LEG_BLOCKS = 12;
  private final WanderBehavior search = new WanderBehavior();
  private boolean searching;
  private final WaypointNavigator navigator = new WaypointNavigator();
  private List<Vec3> route = List.of();
  private int next;
  private long nextPlan;
  private UUID target;
  private Vec3 goal;

  public static List<ResourceLocation> preyTypes(ServerSubLevel body) {
    var brain = SubLevelBrains.get(SubLevelCreatureParts.root(body));
    for (int i = 0; i < brain.capacity(); i++) {
      var card = brain.inventory().getItem(i);
      if (card.is(
          org.azkerash.createbioships.components.ComponentRegistry.HUNTING_BEHAVIOR_CARD.get())) {
        return HuntingCardData.read(card);
      }
    }
    return List.of();
  }

  public static boolean isPrey(
      ServerSubLevel body, LivingEntity prey, List<ResourceLocation> types) {
    if (prey == null
        || !prey.isAlive()
        || prey.isSpectator()
        || prey instanceof Player
        || !types.contains(BuiltInRegistries.ENTITY_TYPE.getKey(prey.getType()))) {
      return false;
    }
    var access = SubLevelPermissions.get(body);
    if (access.permits(prey.getUUID())) {
      return false;
    }
    if (prey instanceof net.minecraft.world.entity.TamableAnimal pet
        && pet.getOwnerUUID() != null
        && access.permits(pet.getOwnerUUID())) {
      return false;
    }
    if (prey instanceof org.azkerash.createbioships.combat.SubLevelCombatTarget construct) {
      return construct.body() != null
          && SubLevelCreatureParts.root(construct.body()) != SubLevelCreatureParts.root(body)
          && !SubLevelHealth.status(construct.body()).dead()
          && (access.owner() == null
              || !Objects.equals(
                  access.owner(), SubLevelPermissions.get(construct.body()).owner()));
    }
    return true;
  }

  @Override
  public void tick(ServerSubLevel body) {
    var types = preyTypes(body);
    var weapons = HuntingWeapons.mounts(body);
    if (types.isEmpty() || weapons.isEmpty()) {
      stop(body);
      return;
    }
    var position = SubLevelPathfinder.position(body);
    // Reuse the two-eye sight cache, rather than casting again for every candidate or eye.
    var prey =
        SubLevelVision.detected(body).stream()
            .map(s -> body.getLevel().getEntity(s.entity()))
            .filter(e -> e instanceof LivingEntity living && isPrey(body, living, types))
            .map(e -> (LivingEntity) e)
            .min(
                Comparator.comparingDouble(
                    e -> e.getUUID().equals(target) ? -1 : e.distanceToSqr(position)))
            .orElse(null);
    if (prey == null) {
      if (!searching) {
        stop(body);
        searching = true;
      }
      search.tick(body);
      return;
    }
    if (searching) {
      search.stop(body);
      searching = false;
    }
    var mode =
        org.azkerash.createbioships.core.locomotion.SubLevelLocomotion.get(body).hasFins()
                && body.getLevel()
                    .getFluidState(BlockPos.containing(position))
                    .is(net.minecraft.tags.FluidTags.WATER)
            ? SubLevelPathfinder.Mode.WATER
            : SubLevelPathfinder.Mode.GROUND;
    if (!SubLevelPathfinder.allowed(body, mode)) {
      stop(body);
      return;
    }
    for (var mount : weapons) {
      if (mount.weapon().attack(mount.body(), mount.cell(), prey)) {
        break;
      }
    }
    var destination = prey.position();
    var offset = destination.subtract(position);
    if (mode == SubLevelPathfinder.Mode.GROUND) {
      offset = new Vec3(offset.x, 0, offset.z);
    }
    // Body clearance excludes the legs' wide support polygon: otherwise a huntsman
    // stops several blocks before its jaws can ever reach the victim.
    var bounds = body.boundingBox();
    double approach =
        Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) / 2
            + APPROACH_MARGIN;
    if (offset.length() <= approach) {
      var steering =
          org.azkerash.createbioships.core.locomotion.SubLevelFacing.towards(
              org.azkerash.createbioships.core.locomotion.SubLevelFacing.world(body),
              offset,
              0,
              mode == SubLevelPathfinder.Mode.WATER);
      BrainMotorControls.move(body, Vec3.ZERO, steering.yawRate(), MOTOR_LEASE_TICKS, mode);
      return;
    }
    long now = body.getLevel().getGameTime();
    if (!prey.getUUID().equals(target)
        || now >= nextPlan
        || goal == null
        || goal.distanceToSqr(destination) > REPLAN_DISTANCE_SQUARED) {
      target = prey.getUUID();
      goal = destination;
      nextPlan = now + REPLAN_TICKS;
      var endpoint =
          position.add(
              offset.normalize().scale(Math.min(ROUTE_LEG_BLOCKS, offset.length() - approach)));
      route = SubLevelPathfinder.find(body, endpoint, mode).points();
      next = 0;
      navigator.reset();
    }
    if (route.isEmpty()) {
      BrainMotorControls.stop(body);
      return;
    }
    next = navigator.follow(body, route, next, CHASE_SPEED, MOTOR_LEASE_TICKS, mode).next();
  }

  @Override
  public void stop(ServerSubLevel body) {
    if (searching) {
      search.stop(body);
      searching = false;
    }
    navigator.reset();
    route = List.of();
    target = null;
    goal = null;
    BrainBehavior.super.stop(body);
  }
}
