package org.Azkerash.create_bioships;

import static org.Azkerash.create_bioships.MaterialCost.*;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.*;
import org.Azkerash.create_bioships.BioshipAdaptation.Proposal;

/** Read-only proposal generation; payment and body mutation belong to the adaptation actuator. */
final class BioshipAdaptationPlanner {
  record State(Map<String, Integer> reserve, Set<BlockPos> armor, long nextWork) {
    State {
      reserve = Map.copyOf(reserve);
      armor = Set.copyOf(armor);
    }
  }

  private static boolean clear(LivingCoreBlockEntity core, BlockPos pos) {
    if (!core.ship().getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))) return false;
    if (!core.getLevel().getBlockState(pos).isAir() || !DigestiveSystem.permitsPlacement(core, pos))
      return false;
    var p = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(pos));
    if (!BioshipIntegration.growthSpace(core.getLevel(), BlockPos.containing(p))) return false;
    return core.getLevel()
        .getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            new AABB(p.x - .5, p.y - .5, p.z - .5, p.x + .5, p.y + .5, p.z + .5),
            e -> !(e instanceof BioshipTargetEntity))
        .isEmpty();
  }

  private static BlockPos organPort(LivingCoreBlockEntity core, boolean mouth) {
    var preferred =
        mouth ? core.layoutPos(new BlockPos(3, 2, 3)) : core.layoutPos(new BlockPos(-2, 2, 3));
    if (core.isMature() && clear(core, preferred)) return preferred;
    for (var face :
        List.of(Direction.NORTH, Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.UP)) {
      var pos = core.getBlockPos().relative(face);
      if (clear(core, pos)) return pos;
    }
    return null;
  }

  static List<Proposal> plan(LivingCoreBlockEntity core, State memory) {
    var mounting = BioshipMountGrowth.plan(core);
    var result = offers(core, memory, mounting);
    if (mounting != null) {
      var held = mounting.completeCost();
      result.removeIf(
          p ->
              p.urgency() < BioshipMountGrowth.URGENCY
                  && p.flesh() + p.fiber() + p.bone() > 0
                  && !EmergencyReserve.after(
                      core,
                      new MaterialCost(
                          held.flesh() + p.flesh(),
                          held.fiber() + p.fiber(),
                          held.bone() + p.bone())));
    }
    return result;
  }

  private static List<Proposal> offers(
      LivingCoreBlockEntity core, State memory, BioshipMountGrowth.Plan mounting) {
    var result = new ArrayList<Proposal>();
    if (!BioshipBodyBehavior.blocker(core).isEmpty()) return result;
    var stomach = DigestiveSystem.stomach(core);
    var gutRepair = BioshipDigestiveRepair.proposal(core);
    if (gutRepair != null) result.add(gutRepair);
    var respiration = BioshipRespiration.proposal(core);
    if (respiration != null) result.add(respiration);
    var anatomy = BioshipAnatomy.proposal(core);
    if (anatomy != null) result.add(anatomy);
    var options = BioshipRecovery.options(core);
    if (!options.mouth()
        && !core.hasMouth()
        && memory.reserve().getOrDefault(FLESH, 0) >= 8
        && memory.reserve().getOrDefault(FIBER, 0) >= 4
        && memory.reserve().getOrDefault(BONE, 0) >= 2) {
      var pos = organPort(core, true);
      if (pos != null)
        result.add(
            new Proposal(
                BioshipBrain.Action.REGROW_MOUTH,
                pos,
                Create_bioships.FEEDING_MOUTH.get().defaultBlockState(),
                8,
                4,
                2,
                1150,
                "Regrow the mouth from core emergency reserves"));
    }
    if (!options.stomach()
        && stomach == null
        && memory.reserve().getOrDefault(FLESH, 0) >= 16
        && memory.reserve().getOrDefault(FIBER, 0) >= 8
        && memory.reserve().getOrDefault(BONE, 0) >= 4) {
      var pos = organPort(core, false);
      if (pos != null)
        result.add(
            new Proposal(
                BioshipBrain.Action.REGROW_STOMACH,
                pos,
                Create_bioships.STOMACH.get().defaultBlockState(),
                16,
                8,
                4,
                1130,
                "Regrow the stomach from core emergency reserves"));
    }
    if (result.isEmpty()) {
      var sacrifice = BioshipRecovery.sacrifice(core, memory.reserve(), options);
      if (sacrifice != null)
        result.add(
            new Proposal(
                BioshipBrain.Action.RECLAIM_COMPONENT,
                sacrifice,
                core.getLevel().getBlockState(sacrifice),
                0,
                0,
                0,
                1120,
                "Reclaim nonessential tissue into resources for digestive recovery"));
    }
    if (stomach == null || !core.hasMouth() || !stomach.connected(core)) return result;
    var legs = BioshipLegGrowth.proposal(core);
    if (legs != null) result.add(legs);
    var mounts = BioshipMountGrowth.proposal(core, mounting);
    if (mounts != null) result.add(mounts);
    var grasperEvolution = BioshipGrasperEvolution.proposal(core);
    if (grasperEvolution != null) result.add(grasperEvolution);
    var evolution = BioshipLegEvolution.proposal(core);
    if (evolution != null) result.add(evolution);
    var voice = BioshipLarynx.proposal(core);
    if (voice != null) result.add(voice);
    var room = core.engineRoom().quote(core);
    if (room != null
        && !BioshipNutrition.critical(core)
        && EmergencyReserve.after(core, room.completeCost(core)))
      result.add(
          new Proposal(
              room.legUpgrades() > 0
                  ? BioshipBrain.Action.STRENGTHEN_LEGS
                  : BioshipBrain.Action.GROW_ENGINE_ROOM,
              core.getBlockPos(),
              BioshipTissue.body().defaultBlockState(),
              room.cost().flesh(),
              room.cost().fiber(),
              room.cost().bone(),
              940,
              "Expand the engine room with a fully funded digestive and leg budget"));
    var digestive = BioshipDigestiveGrowth.plan(core);
    if (digestive != null)
      result.add(
          new Proposal(
              digestive.legUpgrades() > 0
                  ? BioshipBrain.Action.STRENGTHEN_LEGS
                  : BioshipBrain.Action.GROW_DIGESTION,
              digestive.mouth(),
              Create_bioships.FEEDING_MOUTH.get().defaultBlockState(),
              digestive.cost().flesh(),
              digestive.cost().fiber(),
              digestive.cost().bone(),
              900,
              "Expand digestion to match body energy demand"));
    boolean overloaded =
        !core.canFly() && core.crawl().carriedLoad(core) > core.crawl().loadCapacity();
    if (overloaded && !core.crawl().canEnhance(core)) {
      var source = shedCandidate(core, stomach, false, memory.armor());
      if (source != null)
        result.add(
            new Proposal(
                BioshipBrain.Action.RECLAIM_COMPONENT,
                source,
                core.getLevel().getBlockState(source),
                0,
                0,
                0,
                1080,
                "Reclaim nonessential mass to regain leg support"));
    }
    if (BioshipNutrition.critical(core) || core.getLevel().getGameTime() < memory.nextWork())
      return result;
    int count = BioshipStorage.sacks(core).size();
    if (BioshipStores.total(stomach) > BioshipStores.capacity(stomach)
        && funded(stomach, 8, 4, 1)) {
      var pos = DigestiveSystem.storagePort(core, core.getBlockPos());
      if (pos != null) {
        int tubes = DigestiveSystem.storageCost(core, pos);
        var state =
            BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("biomancy:storage_sac"))
                .defaultBlockState();
        double extra = core.crawl().blockLoad(core, pos, state);
        boolean stronger =
            !core.canFly() && core.crawl().carriedLoad(core) + extra > core.crawl().loadCapacity();
        if (tubes >= 0
            && funded(
                stomach,
                8 + tubes + (stronger ? 8 : 0),
                4 + tubes + (stronger ? 4 : 0),
                stronger ? 3 : 1)
            && EmergencyReserve.after(
                core,
                new MaterialCost(
                    8 + tubes + (stronger ? 8 : 0),
                    4 + tubes + (stronger ? 4 : 0),
                    stronger ? 3 : 1))
            && (!stronger || core.crawl().canEnhance(core))
            && BioshipSupport.safeAddition(core, pos, state))
          result.add(
              new Proposal(
                  stronger ? BioshipBrain.Action.STRENGTHEN_LEGS : BioshipBrain.Action.GROW_STORAGE,
                  pos,
                  state,
                  8 + tubes,
                  4 + tubes,
                  1,
                  900,
                  stronger
                      ? "Strengthen legs before growing storage"
                      : "Grow a connected sac for construction-material overflow"));
      }
    }
    if (count > 1
        && BioshipStores.total(stomach)
            < BioshipStores.capacity(stomach) - BioshipStores.SAC_CAPACITY - 64) {
      var source = shedCandidate(core, stomach, true, memory.armor());
      if (source != null)
        result.add(
            new Proposal(
                BioshipBrain.Action.RECLAIM_COMPONENT,
                source,
                core.getLevel().getBlockState(source),
                0,
                0,
                0,
                250,
                "Reabsorb an empty excess storage sac"));
    }
    if (!overloaded && !core.buildingHull()) {
      var protection = BioshipAnatomy.protection(core);
      if (protection != null) result.add(protection);
    }
    return result;
  }

  private static boolean funded(StomachBlockEntity stomach, int flesh, int fiber, int bone) {
    return new MaterialCost(flesh, fiber, bone).funded(stomach);
  }

  private static BlockPos shedCandidate(
      LivingCoreBlockEntity core,
      StomachBlockEntity stomach,
      boolean sacsOnly,
      Set<BlockPos> armor) {
    var body = new HashSet<>(BioshipDecay.blocks(core.ship()));
    for (var pos : body) {
      var state = core.getLevel().getBlockState(pos);
      var be = core.getLevel().getBlockEntity(pos);
      boolean sac =
          be != null
              && EngineRoom.storage(be)
              && BioshipScavenging.emptyComponent(core, pos)
              && (sacsOnly
                  ? be.getPersistentData().getBoolean(BioshipStores.AUTOMATIC)
                  : BioshipStores.connected(stomach).size() > 1);
      boolean spare =
          be != null
              && !BioshipIntegration.isMaw(state)
              && !(be instanceof StomachBlockEntity)
              && BioshipScavenging.emptyComponent(core, pos)
              && BioshipIntegration.blockEntities(core.ship()).stream()
                      .filter(other -> other.getBlockState().is(state.getBlock()))
                      .count()
                  > 1;
      if (sacsOnly ? !sac : !armor.contains(pos) && !sac && !spare) continue;
      if (!sacsOnly
          && be != null
          && be.getPersistentData().getBoolean("BioshipHostFlesh")
          && core.crawl().blockLoad(core, pos, state)
              <= core.crawl().blockLoad(core, pos, BioshipTissue.body().defaultBlockState()))
        continue;
      if (pos.equals(core.getBlockPos()) || stomach.gutPath.contains(pos)) continue;
      if (!connectedWithout(body, pos, core.getBlockPos())) continue;
      return pos;
    }
    return null;
  }

  private static boolean connectedWithout(Set<BlockPos> body, BlockPos removed, BlockPos root) {
    var cells = new HashSet<>(body);
    cells.remove(removed);
    var seen = new HashSet<BlockPos>();
    var queue = new ArrayDeque<BlockPos>();
    queue.add(root);
    seen.add(root);
    while (!queue.isEmpty()) {
      var point = queue.removeFirst();
      for (var face : Direction.values()) {
        var next = point.relative(face);
        if (cells.contains(next) && seen.add(next)) queue.add(next);
      }
    }
    return seen.size() == cells.size();
  }
}
