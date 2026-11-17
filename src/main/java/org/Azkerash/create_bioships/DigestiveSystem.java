package org.Azkerash.create_bioships;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.Azkerash.create_bioships.amoeba.transport.ComponentTransport;
import org.Azkerash.create_bioships.creature.resources.MaterialCost;
import org.Azkerash.create_bioships.disabled.wip.EngineRoom;
import org.Azkerash.create_bioships.integration.biomancy.NativeStorage;
import org.Azkerash.create_bioships.registry.Create_bioships;

/**
 * Food crosses assigned physical intestine networks; construction products enter shared storage.
 */
public final class DigestiveSystem {
  public static StomachBlockEntity stomach(LivingCoreBlockEntity core) {
    var organs = stomachs(core);
    var preferred = core.layoutPos(new BlockPos(-2, 2, 3));
    return organs.stream()
        .sorted(
            Comparator.comparing((StomachBlockEntity s) -> !s.connected(core))
                .thenComparing(s -> !s.getBlockPos().equals(preferred))
                .thenComparingLong(s -> s.getBlockPos().asLong()))
        .findFirst()
        .orElse(null);
  }

  public static List<StomachBlockEntity> stomachs(LivingCoreBlockEntity core) {
    return core.ship() == null
        ? List.of()
        : BioshipIntegration.blockEntities(core.ship()).stream()
            .filter(e -> e instanceof StomachBlockEntity)
            .map(e -> (StomachBlockEntity) e)
            .sorted(Comparator.comparingLong(e -> e.getBlockPos().asLong()))
            .toList();
  }

  public static List<BlockPos> mouths(LivingCoreBlockEntity core) {
    return DigestiveIntakes.routes(core).mouths().stream()
        .map(StomachMultiblock::pos)
        .sorted(Comparator.comparingLong(BlockPos::asLong))
        .toList();
  }

  public static int requiredPairs(LivingCoreBlockEntity core) {
    return BlockEnergyTable.pairs(BioshipEnergy.demand(core));
  }

  static StomachBlockEntity forMouth(LivingCoreBlockEntity core, BlockPos mouth) {
    return DigestiveIntakes.forMouth(core, mouth);
  }

  static boolean linked(LivingCoreBlockEntity core, BlockPos a, BlockPos b) {
    for (var direction : Direction.values())
      if (a.relative(direction).equals(b)) {
        var first = core.getLevel().getBlockState(a);
        var second = core.getLevel().getBlockState(b);
        return IntestineBlock.compatible(first)
            && IntestineBlock.compatible(second)
            && (!first.is(Create_bioships.INTESTINE.get())
                || first.getValue(IntestineBlock.CONNECTIONS.get(direction)))
            && (!second.is(Create_bioships.INTESTINE.get())
                || second.getValue(IntestineBlock.CONNECTIONS.get(direction.getOpposite())));
      }
    return false;
  }

  public static boolean connected(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    return StomachConnections.connected(core, stomach);
  }

  private static List<BlockPos> findPath(
      LivingCoreBlockEntity core, ServerSubLevel ship, BlockPos start, BlockPos end) {
    return findPath(core, ship, start, end, null, null);
  }

  private static List<BlockPos> findPath(
      LivingCoreBlockEntity core,
      ServerSubLevel ship,
      BlockPos start,
      BlockPos end,
      BlockPos blocked,
      BlockPos freed) {
    var preferred = findPath(core, ship, start, end, blocked, freed, false, Set.of());
    return preferred != null
        ? preferred
        : findPath(core, ship, start, end, blocked, freed, true, Set.of());
  }

  private static List<BlockPos> findPath(
      LivingCoreBlockEntity core,
      ServerSubLevel ship,
      BlockPos start,
      BlockPos end,
      BlockPos blocked,
      BlockPos freed,
      boolean spatial,
      Set<BlockPos> excluded) {
    var level = ship.getLevel();
    var origin = core.structuralOrigin();
    int low = spatial ? Math.min(-2, Math.min(start.getY(), end.getY()) - origin.getY() - 1) : 0;
    int high = spatial ? Math.max(6, Math.max(start.getY(), end.getY()) - origin.getY() + 2) : 6;
    Map<BlockPos, BlockPos> parent = new HashMap<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(start);
    parent.put(start, start);
    while (!queue.isEmpty() && parent.size() < 12000) {
      var point = queue.removeFirst();
      if (point.equals(end)) {
        List<BlockPos> path = new ArrayList<>();
        var previous = parent.get(end);
        while (!previous.equals(start)) {
          path.add(previous);
          previous = parent.get(previous);
        }
        Collections.reverse(path);
        return path;
      }
      var directions = new ArrayList<Direction>(List.of(Direction.values()));
      // Route equal-length connections over the core, leaving connected tissue for a higher eye.
      directions.sort(
          Comparator.comparingInt(
              d -> point.relative(d).equals(core.getBlockPos().above()) ? 0 : 1));
      for (var direction : directions) {
        var next = point.relative(direction);
        var offset = next.subtract(origin);
        if (parent.containsKey(next)
            || !next.equals(end) && excluded.contains(next)
            || Math.abs(offset.getX()) > 24
            || offset.getY() < low
            || offset.getY() > high
            || Math.abs(offset.getZ()) > 24) continue;
        if (!spatial
            && !next.equals(end)
            && offset.getY() != 1
            && !next.equals(origin)
            && !next.equals(freed)
            && !level.getBlockState(next).is(Create_bioships.INTESTINE.get())) continue;
        if (!ship.getPlot().contains(new net.minecraft.world.level.ChunkPos(next))) continue;
        if (core.organMove() != null && next.equals(core.organMove().target()) && !next.equals(end))
          continue;
        var state =
            next.equals(blocked)
                ? Create_bioships.CORE.get().defaultBlockState()
                : next.equals(freed)
                    ? Create_bioships.INTESTINE.get().defaultBlockState()
                    : level.getBlockState(next);
        if (!next.equals(end)
            && !state.isAir()
            && !BioshipTissue.isTissue(state)
            && !state.is(Create_bioships.INTESTINE.get())) continue;
        if (!next.equals(end)) {
          Vec3 physical = ship.logicalPose().transformPosition(Vec3.atCenterOf(next));
          if (!BioshipIntegration.aquaticGrowthSpace(level, BlockPos.containing(physical)))
            continue;
        }
        parent.put(next, point);
        queue.addLast(next);
      }
    }
    return null;
  }

  /** Reserves a complete external intake route without routing through the future solid organ. */
  public static boolean reserveGroupRoute(
      LivingCoreBlockEntity core, List<BlockPos> sources, BlockPos delta) {
    var targets = new HashSet<BlockPos>();
    sources.forEach(p -> targets.add(p.offset(delta)));
    StomachBlockEntity payer = null;
    List<BlockPos> best = null;
    int cost = Integer.MAX_VALUE;
    for (var source : sources) {
      var entity = core.getLevel().getBlockEntity(source);
      if (!(entity instanceof StomachBlockEntity stomach)) return false;
      var end = source.offset(delta);
      for (var mouth : mouths(core)) {
        var path = findPath(core, core.ship(), mouth, end, null, source, true, targets);
        if (path == null) continue;
        var proposal = plan(core, stomach, new LinkedHashSet<>(path), Set.of(), tubes(core));
        if (proposal.purchases < cost && affordable(stomach, proposal)) {
          payer = stomach;
          best = path;
          cost = proposal.purchases;
        }
      }
    }
    if (payer == null || best == null) return false;
    if (!new MaterialCost(cost, cost, 0).pay(payer)) return false;
    payer.paidTubeStock += cost;
    payer.setChanged();
    return true;
  }

  public static boolean permitsPlacement(LivingCoreBlockEntity core, BlockPos pos) {
    return !BioshipRespiration.blocksOpening(core, pos)
        && stomachs(core).stream()
            .noneMatch(
                s ->
                    s.gutPath.contains(pos)
                        || s.sacPaths.values().stream().anyMatch(path -> path.contains(pos)));
  }

  static List<BlockPos> repairPath(
      LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos freed) {
    if (stomach.connectedMouth == null) return null;
    return findPath(core, core.ship(), stomach.connectedMouth, stomach.getBlockPos(), null, freed);
  }

  static int repairPurchases(
      LivingCoreBlockEntity core, StomachBlockEntity stomach, List<BlockPos> route) {
    if (route == null) return 0;
    return plan(
            core,
            stomach,
            new LinkedHashSet<>(route),
            stomach.tubeBootstrapped ? Set.of() : new HashSet<>(route),
            tubes(core))
        .purchases;
  }

  private record Plan(Set<BlockPos> wanted, List<BlockPos> retired, int purchases) {}

  private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> tubes(
      LivingCoreBlockEntity core) {
    Map<BlockPos, net.minecraft.world.level.block.state.BlockState> result = new LinkedHashMap<>();
    for (var p : BioshipDecay.blocks(core.ship())) {
      var state = core.getLevel().getBlockState(p);
      if (state.is(Create_bioships.INTESTINE.get())) result.put(p, state);
    }
    return result;
  }

  private static boolean removable(BlockPos point, Set<BlockPos> cells) {
    var neighbors = new ArrayList<BlockPos>();
    for (var d : Direction.values())
      if (cells.contains(point.relative(d))) neighbors.add(point.relative(d));
    if (neighbors.size() < 2) return true;
    Set<BlockPos> seen = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(neighbors.getFirst());
    seen.add(neighbors.getFirst());
    while (!queue.isEmpty() && seen.size() < 20000) {
      var p = queue.removeFirst();
      for (var d : Direction.values()) {
        var n = p.relative(d);
        if (!n.equals(point) && cells.contains(n) && seen.add(n)) queue.add(n);
      }
    }
    return seen.containsAll(neighbors);
  }

  private static boolean coreLink(
      LivingCoreBlockEntity core, BlockPos removed, Set<BlockPos> cells, Set<BlockPos> wanted) {
    if (core.getBlockPos().equals(core.structuralOrigin())) return true;
    Set<BlockPos> seen = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(core.getBlockPos());
    seen.add(core.getBlockPos());
    while (!queue.isEmpty() && seen.size() < 1000) {
      var point = queue.removeFirst();
      if (point.equals(core.structuralOrigin())) return true;
      for (var d : Direction.values()) {
        var next = point.relative(d);
        if (!next.equals(removed)
            && cells.contains(next)
            && (wanted.contains(next) || CoreMotion.tissue(core.getLevel().getBlockState(next)))
            && seen.add(next)) queue.add(next);
      }
    }
    return false;
  }

  public static boolean reserveOrganRoute(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target) {
    return reserveOrganRoute(core, source, target, true);
  }

  public static boolean reserveOrganRoute(
      LivingCoreBlockEntity core, BlockPos source, BlockPos target, boolean reserve) {
    var stomach =
        stomachs(core).stream()
            .filter(
                s ->
                    source.equals(s.getBlockPos())
                        || source.equals(s.connectedMouth)
                        || s.sacPaths.containsKey(source))
            .findFirst()
            .orElse(stomach(core));
    boolean sourceIsMouth = BioshipIntegration.isMaw(core.getLevel().getBlockState(source));
    if (sourceIsMouth) {
      var intake = forMouth(core, source);
      if (intake != null) stomach = intake;
    }
    var mouth =
        sourceIsMouth
            ? source
            : stomach != null && stomach.connectedMouth != null
                ? stomach.connectedMouth
                : core.mouthBlock();
    if (stomach == null || mouth == null || !stomach.tubeBootstrapped) return true;
    boolean movesMouth = source.equals(mouth), movesStomach = source.equals(stomach.getBlockPos());
    if (movesStomach)
      for (var neighbor : stomachs(core))
        if (neighbor != stomach && StomachConnections.retainsConnection(core, neighbor, stomach))
          for (var face : Direction.values())
            if (target.relative(face).equals(neighbor.getBlockPos())) return true;
    var sourceEntity = core.getLevel().getBlockEntity(source);
    boolean movesStore =
        stomach.sacPaths.containsKey(source)
            || sourceEntity != null && NativeStorage.isStorage(sourceEntity);
    if (!movesMouth && !movesStomach && !movesStore) return true;
    var end = movesStomach ? target : stomach.getBlockPos();
    var start = movesMouth ? target : mouth;
    var main = findPath(core, core.ship(), start, end, target, source);
    if (main == null) return false;
    var wanted = new LinkedHashSet<>(main);
    for (var sac : stomach.sacPaths.keySet()) {
      var path =
          findPath(
              core,
              core.ship(),
              end,
              movesStore && sac.equals(source) ? target : sac,
              target,
              source);
      if (path == null) return false;
      wanted.addAll(path);
    }
    if (movesStore && !stomach.sacPaths.containsKey(source)) {
      var path = findPath(core, core.ship(), end, target, target, source);
      if (path == null) return false;
      wanted.addAll(path);
    }
    var result = plan(core, stomach, wanted, Set.of(), tubes(core), mouth);
    if (!affordable(stomach, result)) return false;
    if (reserve && result.purchases > 0) {
      new MaterialCost(result.purchases, result.purchases, 0).pay(stomach);
      stomach.paidTubeStock += result.purchases;
      stomach.setChanged();
    }
    return true;
  }

  static int storageCost(LivingCoreBlockEntity core, BlockPos target) {
    var stomach = stomach(core);
    if (stomach == null || core.mouthBlock() == null) return -1;
    var main =
        findPath(
            core,
            core.ship(),
            stomach.connectedMouth != null ? stomach.connectedMouth : core.mouthBlock(),
            stomach.getBlockPos());
    var branch = findPath(core, core.ship(), stomach.getBlockPos(), target);
    if (main == null || branch == null) return -1;
    var wanted = new LinkedHashSet<BlockPos>(main);
    wanted.addAll(branch);
    stomach.sacPaths.values().forEach(wanted::addAll);
    return plan(core, stomach, wanted, Set.of(), tubes(core)).purchases;
  }

  /** Prefer a real connection to the digestive network over an arbitrary attachment to the core. */
  static BlockPos storagePort(LivingCoreBlockEntity core, BlockPos source) {
    var stomach = stomach(core);
    var mouth =
        stomach != null && stomach.connectedMouth != null
            ? stomach.connectedMouth
            : core.mouthBlock();
    if (stomach == null || mouth == null) return null;
    var main = findPath(core, core.ship(), mouth, stomach.getBlockPos());
    if (main == null) return null;
    var network = new LinkedHashSet<BlockPos>();
    network.addAll(main);
    network.add(stomach.getBlockPos());
    stomach.sacPaths.values().forEach(network::addAll);
    var candidates = new LinkedHashSet<BlockPos>();
    for (var point : network)
      for (var direction : Direction.values()) candidates.add(point.relative(direction));
    var ordered = new ArrayList<>(candidates);
    ordered.sort(Comparator.comparingDouble(p -> p.distSqr(stomach.getBlockPos())));
    var wanted = new LinkedHashSet<>(main);
    stomach.sacPaths.forEach(
        (sac, path) -> {
          if (!sac.equals(source)) wanted.addAll(path);
        });
    var existing = tubes(core);
    for (var candidate : ordered) {
      if (candidate.equals(source)
          || network.contains(candidate)
          || candidate.equals(mouth)
          || candidate.equals(core.getBlockPos())
          || core.isMature() && !EngineRoom.inside(core, candidate)) continue;
      var state = core.getLevel().getBlockState(candidate);
      if (!state.isAir() && !BioshipTissue.isTissue(state)) continue;
      var physical = core.ship().logicalPose().transformPosition(Vec3.atCenterOf(candidate));
      if (!BlockPos.containing(physical).equals(source)
          && !BioshipIntegration.growthSpace(core.getLevel(), BlockPos.containing(physical)))
        continue;
      var path = findPath(core, core.ship(), stomach.getBlockPos(), candidate);
      if (path == null) continue;
      var trial = new LinkedHashSet<>(wanted);
      trial.addAll(path);
      if (affordable(stomach, plan(core, stomach, trial, Set.of(), existing))) return candidate;
    }
    return null;
  }

  static void arrangeStorage(LivingCoreBlockEntity core) {
    var stomach = stomach(core);
    if (stomach == null
        || !stomach.connected(core)
        || ComponentTransport.busy(core.amoebaComponents())
        || core.rescue().active()) return;
    for (var store : BioshipIntegration.blockEntities(core.ship())) {
      var pos = store.getBlockPos();
      if (!NativeStorage.isStorage(store)
          || store.getPersistentData().getBoolean("BioshipManualPlacement")) continue;
      if (connectedStore(core, stomach, pos)) continue;
      var target = storagePort(core, pos);
      if (target != null && ComponentTransport.begin(core.amoebaComponents(), pos, target, false))
        return;
    }
  }

  private static Plan plan(
      LivingCoreBlockEntity core,
      StomachBlockEntity stomach,
      Set<BlockPos> wanted,
      Set<BlockPos> starter,
      Map<BlockPos, net.minecraft.world.level.block.state.BlockState> existing) {
    return plan(core, stomach, wanted, starter, existing, stomach.connectedMouth);
  }

  private static Plan plan(
      LivingCoreBlockEntity core,
      StomachBlockEntity stomach,
      Set<BlockPos> wanted,
      Set<BlockPos> starter,
      Map<BlockPos, net.minecraft.world.level.block.state.BlockState> existing,
      BlockPos replacingMouth) {
    wanted = new LinkedHashSet<>(wanted);
    for (var route : DigestiveIntakes.routes(core).routes())
      if (replacingMouth == null || !StomachMultiblock.pos(route.mouth()).equals(replacingMouth))
        for (var point : route.tubes()) wanted.add(new BlockPos(point.x(), point.y(), point.z()));
    for (var other : stomachs(core))
      if (other != stomach) {
        other.gutPath.stream().filter(existing::containsKey).forEach(wanted::add);
        for (var path : other.sacPaths.values())
          for (var point : path) if (existing.containsKey(point)) wanted.add(point);
      }
    Set<BlockPos> cells = new HashSet<>(BioshipDecay.blocks(core.ship()));
    cells.addAll(wanted);
    var retired = new ArrayList<BlockPos>();
    int reuse = stomach.freeTubeStock + stomach.paidTubeStock;
    for (var entry : existing.entrySet())
      if (!entry.getKey().equals(core.structuralOrigin())
          && !wanted.contains(entry.getKey())
          && !entry.getValue().getValue(IntestineBlock.MANUAL)) {
        if (entry.getValue().getValue(IntestineBlock.ENCASED)
            || removable(entry.getKey(), cells) && coreLink(core, entry.getKey(), cells, wanted)) {
          retired.add(entry.getKey());
          reuse++;
          if (!entry.getValue().getValue(IntestineBlock.ENCASED)) cells.remove(entry.getKey());
        }
      }
    int missing = 0, free = 0;
    for (var p : wanted)
      if (!existing.containsKey(p)) {
        missing++;
        if (starter.contains(p)) free++;
      }
    return new Plan(new LinkedHashSet<>(wanted), retired, Math.max(0, missing - free - reuse));
  }

  private static boolean affordable(StomachBlockEntity stomach, Plan plan) {
    return new MaterialCost(plan.purchases, plan.purchases, 0).funded(stomach);
  }

  static boolean reserveCoreRoute(LivingCoreBlockEntity core, BlockPos target, boolean reserve) {
    for (var owner : stomachs(core))
      if (!reserveCorePairRoute(core, owner, target, false)) return false;
    if (reserve)
      for (var owner : stomachs(core))
        if (!reserveCorePairRoute(core, owner, target, true)) return false;
    return true;
  }

  private static boolean reserveCorePairRoute(
      LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos target, boolean reserve) {
    if (stomach == null) return true;
    boolean crosses =
        stomach.gutPath.contains(target)
            || stomach.sacPaths.values().stream().anyMatch(p -> p.contains(target));
    if (!crosses) return true;
    var main =
        findPath(
            core,
            core.ship(),
            stomach.connectedMouth != null ? stomach.connectedMouth : core.mouthBlock(),
            stomach.getBlockPos(),
            target,
            core.getBlockPos());
    if (main == null) return false;
    var wanted = new LinkedHashSet<>(main);
    for (var sac : stomach.sacPaths.keySet()) {
      var path =
          findPath(core, core.ship(), stomach.getBlockPos(), sac, target, core.getBlockPos());
      if (path == null) return false;
      wanted.addAll(path);
    }
    var existing = tubes(core);
    var skin = existing.remove(target);
    if (skin != null) existing.put(core.getBlockPos(), skin);
    var plan = plan(core, stomach, wanted, Set.of(), existing);
    if (!affordable(stomach, plan)) return false;
    if (reserve && plan.purchases > 0) {
      new MaterialCost(plan.purchases, plan.purchases, 0).pay(stomach);
      stomach.paidTubeStock += plan.purchases;
      stomach.setChanged();
    }
    return true;
  }

  public static void connect(LivingCoreBlockEntity core, ServerSubLevel ship) {
    var mouths = mouths(core);
    var stomachs = stomachs(core);
    // Preserve saved routes as construction hints, not exclusive intake ownership.
    for (var stomach : stomachs) {
      if (stomach.connectedMouth != null && mouths.contains(stomach.connectedMouth))
        connectPair(core, ship, stomach, stomach.connectedMouth, true);
      else {
        var established =
            DigestiveIntakes.routes(core).routes().stream()
                .filter(route -> route.stomach() == stomach)
                .findFirst()
                .orElse(null);
        if (established != null)
          connectPair(core, ship, stomach, StomachMultiblock.pos(established.mouth()), true);
      }
    }
    for (var stomach : stomachs)
      if (!stomach.connected(core)) {
        var candidates = new ArrayList<>(mouths);
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(stomach.getBlockPos())));
        for (var mouth : candidates) if (connectPair(core, ship, stomach, mouth, true)) break;
      }
    for (var mouth : mouths)
      if (forMouth(core, mouth) == null) {
        var candidates = new ArrayList<>(stomachs);
        candidates.sort(Comparator.comparingDouble(s -> mouth.distSqr(s.getBlockPos())));
        for (var stomach : candidates) if (connectPair(core, ship, stomach, mouth, false)) break;
      }
    for (var group : StomachConnections.groups(core))
      if (!group.isEmpty() && group.getFirst().connected(core))
        org.Azkerash.create_bioships.creature.digestion.DigestiveFoodRouting.balance(group);
    BioshipDigestiveRepair.invalidate(core);
    BioshipAnatomy.invalidate(core);
  }

  private static boolean connectPair(
      LivingCoreBlockEntity core,
      ServerSubLevel ship,
      StomachBlockEntity stomach,
      BlockPos mouth,
      boolean primary) {
    var main = findPath(core, ship, mouth, stomach.getBlockPos());
    if (main == null) return false;
    var existing = tubes(core);
    Set<BlockPos> starter = stomach.tubeBootstrapped ? Set.of() : new HashSet<>(main);
    var wanted = new LinkedHashSet<>(main);
    var selected = plan(core, stomach, wanted, starter, existing, mouth);
    if (!affordable(stomach, selected)) return false;
    Map<BlockPos, List<BlockPos>> branches = new LinkedHashMap<>();
    var stores =
        BioshipIntegration.blockEntities(ship).stream()
            .filter(NativeStorage::isStorage)
            .filter(
                store ->
                    stomachs(core).stream()
                        .noneMatch(
                            other ->
                                other != stomach
                                    && other.sacPaths.containsKey(store.getBlockPos())))
            .sorted(
                java.util.Comparator.comparing(e -> !stomach.sacPaths.containsKey(e.getBlockPos())))
            .toList();
    for (var store :
        primary ? stores : List.<net.minecraft.world.level.block.entity.BlockEntity>of()) {
      var path = findPath(core, ship, stomach.getBlockPos(), store.getBlockPos());
      if (path == null) continue;
      var trial = new LinkedHashSet<>(wanted);
      trial.addAll(path);
      var candidate = plan(core, stomach, trial, starter, existing, mouth);
      if (affordable(stomach, candidate)) {
        wanted = trial;
        selected = candidate;
        branches.put(store.getBlockPos(), path);
      }
    }
    var level = ship.getLevel();
    for (var next : wanted) {
      var state = level.getBlockState(next);
      if (!state.isAir()
          && !BioshipTissue.isTissue(state)
          && !state.is(Create_bioships.INTESTINE.get())) return false;
    }
    if (!new MaterialCost(selected.purchases, selected.purchases, 0).pay(stomach)) return false;
    stomach.paidTubeStock += selected.purchases;
    for (var old : selected.retired) {
      var state = existing.get(old);
      if (state.getValue(IntestineBlock.PAID)) stomach.paidTubeStock++;
      else stomach.freeTubeStock++;
      level.setBlock(
          old,
          state.getValue(IntestineBlock.ENCASED)
              ? BioshipTissue.body().defaultBlockState()
              : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
          3);
    }
    for (var next : wanted) {
      var old = level.getBlockState(next);
      if (old.is(Create_bioships.INTESTINE.get())) continue;
      boolean paid;
      if (starter.contains(next)) paid = false;
      else if (stomach.freeTubeStock > 0) {
        stomach.freeTubeStock--;
        paid = false;
      } else if (stomach.paidTubeStock > 0) {
        stomach.paidTubeStock--;
        paid = true;
      } else {
        if (!new MaterialCost(1, 1, 0).pay(stomach)) return false;
        paid = true;
      }
      level.setBlock(
          next,
          Create_bioships.INTESTINE
              .get()
              .defaultBlockState()
              .setValue(IntestineBlock.ENCASED, BioshipTissue.isTissue(old))
              .setValue(IntestineBlock.PAID, paid),
          3);
    }
    for (var next : wanted) {
      var state = level.getBlockState(next);
      var connected = Create_bioships.INTESTINE.get().connections(state, level, next);
      if (!state.equals(connected)) level.setBlock(next, connected, 3);
    }
    if (primary) {
      stomach.gutPath.clear();
      stomach.gutPath.addAll(main);
      stomach.sacPaths.clear();
      stomach.sacPaths.putAll(branches);
      stomach.connectedMouth = mouth;
    }
    stomach.tubeBootstrapped = true;
    stomach.setChanged();
    return true;
  }

  private static boolean connectedStore(
      LivingCoreBlockEntity core, StomachBlockEntity stomach, BlockPos pos) {
    return DigestiveIntakes.connectedStores(core, stomach).stream()
        .anyMatch(store -> store.getBlockPos().equals(pos));
  }

  static int foodCount(LivingCoreBlockEntity core) {
    int total = 0;
    var seen = new HashSet<BlockPos>();
    for (var stomach : stomachs(core))
      if (stomach.connected(core)) {
        total += stomach.queuedNutrition();
        for (var store : DigestiveIntakes.connectedStores(core, stomach))
          if (seen.add(store.getBlockPos())) {
            var inventory =
                core.getLevel()
                    .getCapability(Capabilities.ItemHandler.BLOCK, store.getBlockPos(), null);
            if (inventory != null)
              for (int slot = 0; slot < inventory.getSlots(); slot++)
                if (LivingCoreBlockEntity.isMeat(inventory.getStackInSlot(slot)))
                  total += inventory.getStackInSlot(slot).getCount();
          }
      }
    return total;
  }

  /** Shared functional-digestion prerequisite, independent of the upgrade being considered. */
  static boolean functional(LivingCoreBlockEntity core) {
    if (core.ship() == null || core.body().dead()) return false;
    var stomachs = stomachs(core);
    int required = BioshipDigestiveGrowth.target(core);
    return !mouths(core).isEmpty()
        && stomachs.size() >= required
        && stomachs.stream().allMatch(stomach -> stomach.connected(core));
  }

  public static boolean offerFood(LivingCoreBlockEntity core, ItemStack stack, boolean inherited) {
    for (var stomach : stomachs(core)) if (offerFood(core, stomach, stack, inherited)) return true;
    return false;
  }

  static boolean offerFood(
      LivingCoreBlockEntity core, BlockPos mouth, ItemStack stack, boolean inherited) {
    boolean accepted = offerFood(core, forMouth(core, mouth), stack, inherited);
    if (accepted && core.getLevel().getBlockEntity(mouth) instanceof FeedingMouthBlockEntity intake)
      intake.animate(10, false);
    return accepted;
  }

  private static boolean offerFood(
      LivingCoreBlockEntity core, StomachBlockEntity stomach, ItemStack stack, boolean inherited) {
    if (stack.isEmpty() || stomach == null || !stomach.connected(core)) return false;
    var members = new ArrayList<>(StomachConnections.members(core, stomach));
    members.sort(Comparator.comparingInt(StomachBlockEntity::queuedFood));
    for (var member : members)
      if (member.enqueue(stack, inherited)) {
        if (stomach.connectedMouth != null
            && core.getLevel().getBlockEntity(stomach.connectedMouth)
                instanceof FeedingMouthBlockEntity mouth) mouth.animate(10, false);
        return true;
      }
    if (inherited) return false;
    for (var store : DigestiveIntakes.connectedStores(core, stomach)) {
      var inventory =
          core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, store.getBlockPos(), null);
      if (inventory == null) continue;
      var one = stack.copyWithCount(1);
      if (ItemHandlerHelper.insertItemStacked(inventory, one, true).isEmpty())
        return ItemHandlerHelper.insertItemStacked(inventory, one, false).isEmpty();
    }
    return false;
  }

  public static void pullStoredFood(LivingCoreBlockEntity core, StomachBlockEntity stomach) {
    if (!stomach.connected(core) || !core.wantsStoredFood()) return;
    for (var store : DigestiveIntakes.connectedStores(core, stomach)) {
      var inventory =
          core.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, store.getBlockPos(), null);
      if (inventory == null) continue;
      for (int slot = 0; slot < inventory.getSlots(); slot++) {
        var one = inventory.extractItem(slot, 1, true);
        if (one.isEmpty()) continue;
        boolean material =
            one.is(BioshipIntegration.constructionMaterial()) && !LivingCoreBlockEntity.isMeat(one);
        if (material) {
          var taken = inventory.extractItem(slot, 1, false);
          if (!taken.isEmpty()) stomach.retain(taken);
          return;
        }
        if (stomach.canEnqueue(one, false)) {
          var taken = inventory.extractItem(slot, 1, false);
          if (!taken.isEmpty()) stomach.enqueue(taken, false);
          return;
        }
      }
    }
  }
}
