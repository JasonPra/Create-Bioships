package org.Azkerash.create_bioships;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.Azkerash.create_bioships.amoeba.ComponentTransport;

/**
 * The behavior tree owns goal selection. Sensors propose work; actuators may execute only the
 * selected work. Safety interrupts, committed jobs, weighted needs/goals, and idle are evaluated in
 * that order. Digestion and healing remain passive services, not competing navigation owners.
 */
public final class BioshipBrain {
  public enum Action {
    DEAD,
    SURFACE,
    FLEE,
    DEFEND,
    RECONFIGURE,
    MOVE_COMPONENT,
    RESCUE,
    REST,
    PILOT,
    CONFIGURE,
    REGROW_MOUTH,
    REGROW_STOMACH,
    REPAIR_DIGESTION,
    REPOSITION_ANATOMY,
    GROW_LARYNX,
    GROW_LEG,
    GROW_SUPPORT,
    EVOLVE_LEGS,
    EVOLVE_GRASPERS,
    GROW_RESPIRATION,
    GROW_STORAGE,
    GROW_DIGESTION,
    GROW_ENGINE_ROOM,
    RECLAIM_COMPONENT,
    STRENGTHEN_LEGS,
    FORTIFY,
    COLLECT_ITEM,
    ASSIMILATE,
    SCAVENGE,
    HUNT,
    GROW,
    GROW_SITE,
    WANDER,
    IDLE
  }

  public record Decision(
      Action action,
      Vec3 point,
      BlockPos block,
      LivingEntity entity,
      ItemEntity item,
      BehaviorPriority.Offer offer,
      String reason,
      boolean recycle) {
    public Decision(
        Action action,
        Vec3 point,
        BlockPos block,
        LivingEntity entity,
        ItemEntity item,
        BehaviorPriority.Offer offer,
        String reason) {
      this(action, point, block, entity, item, offer, reason, false);
    }

    Decision recycling() {
      return new Decision(action, point, block, entity, item, offer, reason, true);
    }
  }

  private record Needs(
      boolean digestive,
      boolean hungry,
      int food,
      int flesh,
      int bone,
      int fiber,
      boolean growth,
      boolean criticalFood) {
    boolean materials() {
      return flesh > 0 || bone > 0 || fiber > 0;
    }
  }

  private String previous = "", reason = "Idle";
  private Action action = Action.IDLE;
  private boolean growthBlocked;
  private Decision lastDecision;
  private final GoalQueue<Decision> goals = new GoalQueue<>();

  java.util.List<Decision> queuedGoals() {
    return goals.pending();
  }

  private void refreshGoals(List<Decision> proposals) {
    goals.refresh(proposals, Decision::offer, previous);
  }

  private net.minecraft.nbt.ListTag debugPriorities = new net.minecraft.nbt.ListTag();
  private int debugOfferCount;

  net.minecraft.nbt.ListTag priorities() {
    return debugPriorities.copy();
  }

  int offerCount() {
    return debugOfferCount;
  }

  private void rankForDebug(List<Decision> offers) {
    debugOfferCount = offers.size();
    debugPriorities = new net.minecraft.nbt.ListTag();
    var order =
        Comparator.<Decision>comparingInt(d -> d.offer.band().ordinal())
            .thenComparingDouble(d -> d.offer.weight(previous))
            .thenComparing(d -> d.offer.key())
            .reversed();
    for (var candidate : offers.stream().sorted(order).limit(8).toList()) {
      var tag = new CompoundTag();
      tag.putString("Action", candidate.action.name());
      tag.putString("Band", candidate.offer.band().name());
      tag.putInt("Urgency", candidate.offer.urgency());
      tag.putDouble("Weight", candidate.offer.weight(previous));
      tag.putString("Reason", candidate.reason);
      debugPriorities.add(tag);
    }
  }

  Decision lastDecision() {
    return lastDecision;
  }

  public Action action() {
    return action;
  }

  public String reason() {
    return reason;
  }

  void growthBlocked(boolean blocked) {
    growthBlocked = blocked;
  }

  private Needs needs(LivingCoreBlockEntity core) {
    var stomach = DigestiveSystem.stomach(core);
    boolean digestive = core.hasMouth() && stomach != null && stomach.connected(core);
    int food = DigestiveSystem.foodCount(core);
    int upgrades =
        AirshipPrototype.available()
            ? Math.max(0, core.crawl().hullTier(core) - core.crawl().legTier())
            : 0;
    var room = core.engineRoom().quote(core);
    if (room != null) upgrades = Math.max(upgrades, room.legUpgrades());
    int capacityGoal =
        Math.max(BioshipDigestiveGrowth.target(core), room == null ? 0 : room.pairs());
    int extraMouths = Math.max(0, capacityGoal - DigestiveSystem.mouths(core).size());
    int extraStomachs = Math.max(0, capacityGoal - DigestiveSystem.stomachs(core).size());
    int voice =
        digestive
            ? Math.max(
                0,
                (int) BioshipLarynx.missingCount(core)
                    - (int)
                        BioshipIntegration.blockEntities(core.ship()).stream()
                            .filter(e -> BioshipLarynx.isLarynx(e.getBlockState()))
                            .count())
            : 0;
    int legs = digestive ? BioshipLegGrowth.missing(core) : 0;
    int lungs = BioshipRespiration.needsLungs(core) ? 1 : 0;
    int gills = BioshipRespiration.wantsGills(core) && !BioshipRespiration.has(core, true) ? 1 : 0;
    int gutRepair = BioshipDigestiveRepair.purchases(core);
    int repair = (int) Math.ceil(Math.max(0, core.body().capacity() - core.body().health()) / 4);
    int flesh =
        (AirshipPrototype.available() ? core.missingBiomass() : 0)
            + 8 * upgrades
            + repair
            + gutRepair
            + (room == null ? 0 : room.cost().flesh())
            + EmergencyReserve.TARGET.flesh()
            + 8 * extraMouths
            + 16 * extraStomachs
            + 8 * voice
            + 4 * legs
            + 6 * lungs
            + 6 * gills;
    int bone =
        (core.frameFunded() ? 0 : 8)
            + 2 * upgrades
            + (room == null ? 0 : room.cost().bone())
            + EmergencyReserve.TARGET.bone()
            + 2 * extraMouths
            + 4 * extraStomachs
            + 2 * voice
            + legs;
    int fiber =
        (core.frameFunded() ? 0 : 12)
            + 4 * upgrades
            + (room == null ? 0 : room.cost().fiber())
            + EmergencyReserve.TARGET.fiber()
            + 4 * extraMouths
            + 8 * extraStomachs
            + 4 * voice
            + 2 * legs
            + gutRepair
            + 3 * lungs
            + 6 * gills;
    var mounting = BioshipMountGrowth.demand(core);
    flesh = Math.max(flesh, EmergencyReserve.TARGET.flesh() + mounting.flesh());
    fiber = Math.max(fiber, EmergencyReserve.TARGET.fiber() + mounting.fiber());
    bone = Math.max(bone, EmergencyReserve.TARGET.bone() + mounting.bone());
    if (stomach != null) {
      flesh -= stomach.material("biomancy:flesh_bits");
      bone -= stomach.material("biomancy:bone_fragments");
      fiber -= stomach.material("biomancy:elastic_fibers");
    }
    return new Needs(
        digestive,
        core.hungry(),
        food,
        Math.max(0, flesh),
        Math.max(0, bone),
        Math.max(0, fiber),
        !core.isMature(),
        BioshipNutrition.critical(core));
  }

  boolean shouldHunt(LivingCoreBlockEntity core) {
    if (core.ship() == null
        || core.body().dead()
        || core.resting()
        || core.autonomy().creeperPanic()) return false;
    var n = needs(core);
    return n.digestive
        && n.food == 0
        && (n.hungry && (core.huntingAllowed() || n.growth)
            || n.growth && n.materials()
            || (core.huntingAllowed()
                    || EmergencyReserve.missing(core)
                    || DigestiveSystem.stomachs(core).size() < DigestiveSystem.requiredPairs(core))
                && n.materials());
  }

  private Decision fixed(Action next, String why) {
    return remember(
        new Decision(
            next,
            null,
            null,
            null,
            null,
            new BehaviorPriority.Offer(next.name(), BehaviorPriority.Band.SAFETY, 1000, 0, 0),
            why));
  }

  private Decision remember(Decision selected) {
    if (debugPriorities.isEmpty())
      rankForDebug(
          java.util.stream.Stream.concat(
                  java.util.stream.Stream.of(selected), queuedGoals().stream())
              .distinct()
              .toList());
    lastDecision = selected;
    action = selected.action;
    reason = selected.reason;
    previous = selected.offer.key();
    return selected;
  }

  private Decision offer(
      Action next,
      Vec3 point,
      BlockPos block,
      LivingEntity entity,
      ItemEntity item,
      BehaviorPriority.Band band,
      int urgency,
      int value,
      double effort,
      String why) {
    String key =
        next
            + ":"
            + (block != null
                ? block.asLong()
                : entity != null ? entity.getUUID() : item != null ? item.getUUID() : "idle");
    return new Decision(
        next,
        point,
        block,
        entity,
        item,
        new BehaviorPriority.Offer(key, band, urgency, value, effort),
        why);
  }

  private Decision chooseOffers(List<Decision> offers) {
    var best = BehaviorPriority.choose(offers.stream().map(Decision::offer).toList(), previous);
    return offers.stream().filter(d -> d.offer == best).findFirst().orElseThrow();
  }

  private double effort(LivingCoreBlockEntity core, Vec3 point) {
    return point.distanceTo(BioshipBlueprint.physical(core, BlockPos.ZERO));
  }

  boolean interruptsForFood(LivingCoreBlockEntity core) {
    return BioshipNutrition.critical(core) && BioshipNutrition.foodRelated(core, lastDecision);
  }

  private int materialUrgency(Needs n, BlockState state, boolean wreck) {
    if (wreck && n.digestive && n.criticalFood) return 990; // carrion/reserves before live hunting
    if (n.criticalFood
        && n.digestive
        && !BioshipScavenging.component(state)
        && BioshipNutrition.fuelValue(new ItemStack(state.getBlock().asItem())) > 0) return 1000;
    if (wreck && n.materials()) return 820;
    String id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    return n.flesh > 0 && (BioshipTissue.isTissue(state) || id.contains("flesh")) ? 760 : 0;
  }

  private int itemUrgency(Needs n, ItemStack stack) {
    if (n.criticalFood && BioshipNutrition.fuelValue(stack) > 0)
      return 990 + Math.min(60, BioshipNutrition.fuelValue(stack));
    if (LivingCoreBlockEntity.isMeat(stack))
      return n.food == 0 && n.criticalFood
          ? 980
          : n.food == 0 && n.hungry ? 900 : n.materials() ? 850 : 0;
    String id =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    if (n.bone > 0 && id.equals("biomancy:bone_fragments")
        || n.fiber > 0 && id.equals("biomancy:elastic_fibers")
        || n.flesh > 0 && (id.equals("biomancy:flesh_bits") || id.contains("flesh"))) return 850;
    return BioshipScavenging.droppedBlock(stack) && n.materials() ? 850 : 0;
  }

  private Decision source(
      LivingCoreBlockEntity core,
      Needs n,
      BlockPos pos,
      Vec3 point,
      BlockState state,
      int rank,
      boolean wreck) {
    boolean component = BioshipScavenging.component(state);
    boolean emergency =
        rank != 0
            && component
            && n.digestive
            && n.criticalFood
            && BioshipScavenging.emptyComponent(core, pos);
    int urgency =
        rank == 0
            ? 1000
            : rank == -1
                ? 970
                : component && !n.criticalFood
                    ? 950
                    : emergency ? 600 : materialUrgency(n, state, wreck);
    boolean observed = wreck || core.vision().exposed(core, pos);
    var inventory =
        observed ? core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) : null;
    if (inventory != null)
      for (int slot = 0; slot < inventory.getSlots(); slot++) {
        var stack = inventory.getStackInSlot(slot);
        urgency = Math.max(urgency, itemUrgency(n, stack));
        if (component && !n.criticalFood && LivingCoreBlockEntity.isMeat(stack))
          urgency = Math.max(urgency, 960);
      }
    if (wreck && core.assimilation().committed()) urgency = Math.max(urgency, 940);
    var band = urgency > 0 ? BehaviorPriority.Band.NEED : BehaviorPriority.Band.GOAL;
    if (urgency == 0)
      urgency = wreck ? 350 : rank == 1 ? 360 : rank == 2 ? 300 : rank == 3 ? 220 : 100;
    var decision =
        offer(
            wreck ? Action.SCAVENGE : Action.ASSIMILATE,
            point,
            pos,
            null,
            null,
            band,
            urgency,
            wreck
                ? (n.bone > 0 && BioshipScavenging.frameMaterial(state)
                        || n.fiber > 0 && BioshipScavenging.fiberMaterial(state)
                    ? 130
                    : 30)
                : 0,
            effort(core, point),
            rank == 0
                ? "Acquire a missing digestive organ"
                : wreck
                    ? "Reclaim useful wreck tissue before hunting"
                    : emergency
                        ? "Emergency reclamation of a spare component"
                        : component
                            ? "Assimilate a useful component before routine growth"
                            : "Collect a needed component or reserve");
    return emergency ? decision.recycling() : decision;
  }

  BioshipPriorities.Objective bestWorld(LivingCoreBlockEntity core) {
    if (core.ship() == null) return null;
    var n = needs(core);
    var offers = new ArrayList<Decision>();
    var objectives = BioshipPriorities.worldObjectives(core);
    for (var o : objectives) {
      var state = core.vision().sourceState(core, o.pos());
      if (state != null)
        offers.add(source(core, n, o.pos(), Vec3.atCenterOf(o.pos()), state, o.rank(), false));
    }
    if (offers.isEmpty()) {
      core.supplyTarget = null;
      return null;
    }
    var selected = chooseOffers(offers);
    core.supplyTarget = selected.block;
    return objectives.stream().filter(o -> o.pos().equals(selected.block)).findFirst().orElse(null);
  }

  Decision choose(LivingCoreBlockEntity core) {
    debugPriorities.clear();
    debugOfferCount = 0;
    if (core.ship() == null) return fixed(Action.IDLE, "Dormant core");
    if (core.body().dead()) return fixed(Action.DEAD, "Dead organism");
    // Reconstruction locks its approved body layout until the transaction completes.
    if (core.blueprint().busy()) return fixed(Action.RECONFIGURE, "Finish approved reconstruction");
    if (core.aquatic().needsAir()) return fixed(Action.SURFACE, "Surface for breathable air");
    core.autonomy().assess(core);
    if (core.autonomy().fleeing()
        && (core.autonomy().creeperPanic() || !core.autonomy().trustedPilot(core)))
      return fixed(Action.FLEE, "Escape an unsafe creeper or a critical injury");
    if (core.rescue().active())
      return fixed(Action.RESCUE, "Finish carrying an ally through the aperture");
    var threat = BioshipDefense.target(core);
    if (threat != null)
      return remember(
          offer(
              Action.DEFEND,
              core.vision().knownPosition(threat),
              null,
              threat,
              null,
              BehaviorPriority.Band.SAFETY,
              950,
              0,
              effort(core, threat.position()),
              "Defend against an immediate threat"));
    boolean committed =
        ComponentTransport.busy(core.amoebaComponents())
            || core.coreMotion().active()
            || core.assimilation().finishing();
    if (committed && (!BioshipNutrition.critical(core) || BioshipNutrition.movingFood(core)))
      return fixed(Action.MOVE_COMPONENT, "Finish a conserved component or food-recovery move");
    if (core.resting()) return fixed(Action.REST, "Rest command");
    if (core.canFly() && core.autonomy().trustedPilot(core))
      return fixed(Action.PILOT, "Follow the authorized trusted pilot");
    var n = needs(core);
    var offers = new ArrayList<Decision>();
    for (var o : BioshipPriorities.worldObjectives(core)) {
      var state = core.vision().sourceState(core, o.pos());
      if (state != null)
        offers.add(source(core, n, o.pos(), Vec3.atCenterOf(o.pos()), state, o.rank(), false));
    }
    for (var s : BioshipScavenging.visibleSources(core))
      offers.add(
          source(
              core,
              n,
              s.pos(),
              s.point(),
              s.state(),
              BioshipPriorities.rank(core, s.state(), s.pos()),
              true));
    if (n.digestive)
      for (var loot : core.loot().targets(core)) {
        var point = loot.position().add(0, .2, 0);
        if (!core.crawl().reachableGoal(BlockPos.containing(point))) continue;
        offers.add(
            offer(
                Action.COLLECT_ITEM,
                point,
                loot.item() == null ? BlockPos.containing(point) : null,
                null,
                loot.item(),
                BehaviorPriority.Band.NEED,
                n.criticalFood ? 1120 : n.hungry ? 910 : 860,
                100,
                effort(core, point),
                "Recover known kill drops before hunting"));
      }
    if (n.digestive)
      for (var item :
          core.getLevel()
              .getEntitiesOfClass(
                  ItemEntity.class,
                  new AABB(
                          BioshipBlueprint.physical(core, BlockPos.ZERO),
                          BioshipBlueprint.physical(core, BlockPos.ZERO))
                      .inflate(core.vision().maximumDistance(core)),
                  e -> LivingCoreBlockEntity.droppedSupply(e.getItem()))) {
        var point = item.position().add(0, .2, 0);
        if (!core.vision().visible(core, point)) continue;
        if (item.getItem().is(Items.ROTTEN_FLESH) && !core.starving()) continue;
        int urgency = itemUrgency(n, item.getItem());
        if (!core.crawl().reachableGoal(BlockPos.containing(point))) continue;
        offers.add(
            offer(
                Action.COLLECT_ITEM,
                point,
                null,
                null,
                item,
                urgency > 0 ? BehaviorPriority.Band.NEED : BehaviorPriority.Band.GOAL,
                urgency > 0 ? urgency : 250,
                BioshipReach.anchor(core, point).distanceToSqr(point) <= 2.25 ? 80 : 20,
                effort(core, point),
                "Collect accessible food or required growth material"));
      }
    var prey = BioshipDefense.prey(core);
    if (prey != null)
      offers.add(
          offer(
              Action.HUNT,
              core.vision().knownPosition(prey),
              null,
              prey,
              null,
              BehaviorPriority.Band.NEED,
              n.criticalFood ? 980 : n.hungry ? 880 : 650,
              -30,
              effort(core, prey.position()),
              "Hunt only after easier resources for the same need"));
    if (AirshipPrototype.available()
        && (core.buildingHull()
            || !core.isMature() && core.missingBiomass() == 0 && core.growthStockReady()))
      offers.add(
          offer(
              growthBlocked ? Action.GROW_SITE : Action.GROW,
              null,
              null,
              null,
              null,
              BehaviorPriority.Band.GOAL,
              600,
              0,
              0,
              growthBlocked
                  ? "Find room for a fully funded hull"
                  : "Build with the reserved hull and leg budget"));
    offers.add(
        offer(
            core.hatchling() || core.huntingAllowed() ? Action.WANDER : Action.IDLE,
            null,
            null,
            null,
            null,
            BehaviorPriority.Band.IDLE,
            0,
            0,
            0,
            "No reachable urgent need or funded goal"));
    refreshGoals(offers);
    rankForDebug(queuedGoals());
    var best = chooseOffers(queuedGoals());
    if (committed && !BioshipNutrition.foodRelated(core, best))
      return fixed(
          Action.MOVE_COMPONENT, "Conserve collected components while evaluating urgent needs");
    var selected = remember(best);
    core.supplyTarget = selected.action == Action.ASSIMILATE ? selected.block : null;
    return selected;
  }

  boolean allowsConfiguration(LivingCoreBlockEntity core) {
    return core.adaptation().behavior().allowsConfiguration(core);
  }

  MaterialCost materialNeeds(LivingCoreBlockEntity core) {
    var missing = needs(core);
    return new MaterialCost(missing.flesh(), missing.fiber(), missing.bone());
  }

  boolean needsMaterials(LivingCoreBlockEntity core) {
    return needs(core).materials();
  }

  LivingEntity attackTarget(LivingCoreBlockEntity core) {
    var decision = choose(core);
    return decision.action == Action.HUNT || decision.action == Action.DEFEND
        ? decision.entity
        : null;
  }

  Vec3 groundGoal(LivingCoreBlockEntity core) {
    core.crawl().reach(null);
    var d = choose(core);
    switch (d.action) {
      case SURFACE:
        return null;
      case FLEE:
        return core.autonomy().groundEscape(core);
      case WANDER:
        return core.autonomy().groundWander(core);
      case ASSIMILATE:
        return core.assimilation().gatheringPoint(core, d.point);
      case SCAVENGE:
        var wreck = BioshipIntegration.containing(core.getLevel(), d.block);
        return wreck != null
                && BioshipReach.clear(core, d.point, true, 14)
                && BioshipScavenging.physicallyReachable(core, wreck, d.block, d.point)
            ? null
            : d.point;
      case DEFEND:
        return d.point.distanceToSqr(BioshipBlueprint.physical(core, BlockPos.ZERO)) > 6.25
            ? d.point
            : null;
      case HUNT:
        if (BioshipReach.clear(core, d.point, false)) {
          core.crawl().reach(d.point);
          return null;
        }
        return d.point;
      case COLLECT_ITEM:
        return BioshipFoodCollection.collect(core, d.point, d.item);
      default:
        return null;
    }
  }

  Vec3 flightVelocity(LivingCoreBlockEntity core) {
    var selected = choose(core);
    if (selected.action == Action.FLEE)
      return core.autonomy().flight(core, true).scale(core.rescue().active() ? .25 : 1);
    if (selected.action == Action.WANDER) return core.autonomy().flight(core, false);
    var point = groundGoal(core);
    if (point == null) return Vec3.ZERO;
    var here = BioshipBlueprint.physical(core, BlockPos.ZERO);
    var delta = point.subtract(here);
    return GroundRoute.flightClear(
            core, here.add(delta.normalize().scale(Math.min(3, delta.length()))))
        ? delta.normalize().scale(.65)
        : Vec3.ZERO;
  }

  CompoundTag save() {
    var tag = new CompoundTag();
    tag.putString("Action", action.name());
    tag.putString("Previous", previous);
    tag.putString("Reason", reason);
    return tag;
  }

  void load(CompoundTag tag) {
    lastDecision = null;
    debugPriorities.clear();
    debugOfferCount = 0;
    previous = tag.getString("Previous");
    reason = tag.getString("Reason");
    try {
      action = Action.valueOf(tag.getString("Action"));
    } catch (IllegalArgumentException ignored) {
      action = Action.IDLE;
    }
  }
}
